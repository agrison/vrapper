package net.sourceforge.vrapper.vim.modes;

import static net.sourceforge.vrapper.keymap.StateUtils.union;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.changeCaret;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.convertKeyStroke;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.leafBind;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.leafCtrlBind;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.operatorCmdsWithUpperCase;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.state;
import static net.sourceforge.vrapper.keymap.vim.ConstructorWrappers.transitionBind;
import static net.sourceforge.vrapper.vim.commands.ConstructorWrappers.seq;
import net.sourceforge.vrapper.keymap.SpecialKey;
import net.sourceforge.vrapper.keymap.State;
import net.sourceforge.vrapper.keymap.vim.CountingState;
import net.sourceforge.vrapper.keymap.vim.GoThereState;
import net.sourceforge.vrapper.keymap.vim.RegisterState;
import net.sourceforge.vrapper.keymap.vim.TextObjectState;
import net.sourceforge.vrapper.utils.CaretType;
import net.sourceforge.vrapper.utils.LineInformation;
import net.sourceforge.vrapper.utils.Position;
import net.sourceforge.vrapper.utils.StartEndTextRange;
import net.sourceforge.vrapper.vim.EditorAdaptor;
import net.sourceforge.vrapper.vim.Options;
import net.sourceforge.vrapper.vim.VimConstants;
import net.sourceforge.vrapper.vim.commands.BorderPolicy;
import net.sourceforge.vrapper.vim.commands.CenterLineCommand;
import net.sourceforge.vrapper.vim.commands.ChangeModeCommand;
import net.sourceforge.vrapper.vim.commands.ChangeOperation;
import net.sourceforge.vrapper.vim.commands.ChangeToInsertModeCommand;
import net.sourceforge.vrapper.vim.commands.Command;
import net.sourceforge.vrapper.vim.commands.CommandExecutionException;
import net.sourceforge.vrapper.vim.commands.CountIgnoringNonRepeatableCommand;
import net.sourceforge.vrapper.vim.commands.DeleteOperation;
import net.sourceforge.vrapper.vim.commands.DotCommand;
import net.sourceforge.vrapper.vim.commands.InsertLineCommand;
import net.sourceforge.vrapper.vim.commands.LinewiseVisualMotionCommand;
import net.sourceforge.vrapper.vim.commands.MotionCommand;
import net.sourceforge.vrapper.vim.commands.MotionPairTextObject;
import net.sourceforge.vrapper.vim.commands.MotionTextObject;
import net.sourceforge.vrapper.vim.commands.OptionDependentCommand;
import net.sourceforge.vrapper.vim.commands.OptionDependentTextObject;
import net.sourceforge.vrapper.vim.commands.ParenthesisPairTextObject;
import net.sourceforge.vrapper.vim.commands.PasteAfterCommand;
import net.sourceforge.vrapper.vim.commands.PasteBeforeCommand;
import net.sourceforge.vrapper.vim.commands.PlaybackMacroCommand;
import net.sourceforge.vrapper.vim.commands.QuotedTextObject;
import net.sourceforge.vrapper.vim.commands.RecordMacroCommand;
import net.sourceforge.vrapper.vim.commands.RedoCommand;
import net.sourceforge.vrapper.vim.commands.ReplaceCommand;
import net.sourceforge.vrapper.vim.commands.SetMarkCommand;
import net.sourceforge.vrapper.vim.commands.SimpleSelection;
import net.sourceforge.vrapper.vim.commands.StickToEOLCommand;
import net.sourceforge.vrapper.vim.commands.SwapCaseCommand;
import net.sourceforge.vrapper.vim.commands.TextObject;
import net.sourceforge.vrapper.vim.commands.TextOperation;
import net.sourceforge.vrapper.vim.commands.TextOperationTextObjectCommand;
import net.sourceforge.vrapper.vim.commands.UndoCommand;
import net.sourceforge.vrapper.vim.commands.VisualMotionCommand;
import net.sourceforge.vrapper.vim.commands.YankOperation;
import net.sourceforge.vrapper.vim.commands.motions.LineEndMotion;
import net.sourceforge.vrapper.vim.commands.motions.LineStartMotion;
import net.sourceforge.vrapper.vim.commands.motions.Motion;
import net.sourceforge.vrapper.vim.commands.motions.MoveBigWORDEndRight;
import net.sourceforge.vrapper.vim.commands.motions.MoveBigWORDLeft;
import net.sourceforge.vrapper.vim.commands.motions.MoveBigWORDRight;
import net.sourceforge.vrapper.vim.commands.motions.MoveLeft;
import net.sourceforge.vrapper.vim.commands.motions.MoveRight;
import net.sourceforge.vrapper.vim.commands.motions.MoveWordEndRight;
import net.sourceforge.vrapper.vim.commands.motions.MoveWordLeft;
import net.sourceforge.vrapper.vim.commands.motions.MoveWordRight;

public class NormalMode extends CommandBasedMode {

    public static final String KEYMAP_NAME = "Normal Mode Keymap";
    public static final String NAME = "normal mode";
    private static State<TextObject> textObjects;

    public NormalMode(EditorAdaptor editorAdaptor) {
        super(editorAdaptor);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected KeyMapResolver buildKeyMapResolver() {
        State<String> state = union(
                state(
                    leafBind('r', KeyMapResolver.NO_KEYMAP),
                    leafBind('z', KeyMapResolver.NO_KEYMAP),
                    leafBind('q', KeyMapResolver.NO_KEYMAP),
                    leafBind('@', KeyMapResolver.NO_KEYMAP)),
                getKeyMapsForMotions(),
                editorAdaptor.getPlatformSpecificStateProvider().getKeyMaps(NAME));
        final State<String> countEater = new CountConsumingState(state);
        State<String> registerKeymapState = new RegisterKeymapState(KEYMAP_NAME, countEater);
        return new KeyMapResolver(registerKeymapState, KEYMAP_NAME);
    }

    @SuppressWarnings("unchecked")
    public static State<TextObject> textObjects() {
        if (textObjects == null) {
            final Motion wordRight = MoveWordRight.INSTANCE;
            final Motion wordLeft = MoveWordLeft.INSTANCE;
            final Motion wordEndRight = MoveWordEndRight.INSTANCE;
            final TextObject innerWord = new MotionPairTextObject(wordLeft, wordEndRight);
            final TextObject aWord = new MotionPairTextObject(wordLeft, wordRight);
            final TextObject innerWORD = new MotionPairTextObject(MoveBigWORDLeft.INSTANCE, MoveBigWORDEndRight.INSTANCE);
            final TextObject aWORD = new MotionPairTextObject(MoveBigWORDLeft.INSTANCE, MoveBigWORDRight.INSTANCE);
            final TextObject innerBracket = new ParenthesisPairTextObject('(', ')', false);
            final TextObject aBracket = new ParenthesisPairTextObject('(', ')', true);
            final TextObject innerSquareBracket = new ParenthesisPairTextObject('[', ']', false);
            final TextObject aSquareBracket = new ParenthesisPairTextObject('[', ']', true);
            final TextObject innerBrace = new ParenthesisPairTextObject('{', '}', false);
            final TextObject aBrace = new ParenthesisPairTextObject('{', '}', true);
            final TextObject innerAngleBrace = new ParenthesisPairTextObject('<', '>', false);
            final TextObject anAngleBrace = new ParenthesisPairTextObject('<', '>', true);
            final TextObject innerString = new QuotedTextObject('"', false);
            final TextObject aString = new QuotedTextObject('"', true);
            final TextObject innerGraveString = new QuotedTextObject('`', false);
            final TextObject aGraveString = new QuotedTextObject('`', true);
            final TextObject innerChar = new QuotedTextObject('`', false);
            final TextObject aChar = new QuotedTextObject('\'', true);
            textObjects = union(
                        state(
                            transitionBind('i',
                                    leafBind('b', innerBracket),
                                    leafBind('(', innerBracket),
                                    leafBind(')', innerBracket),
                                    leafBind('[', innerSquareBracket),
                                    leafBind(']', innerSquareBracket),
                                    leafBind('B', innerBrace),
                                    leafBind('{', innerBrace),
                                    leafBind('}', innerBrace),
                                    leafBind('<', innerAngleBrace),
                                    leafBind('>', innerAngleBrace),
                                    leafBind('"', innerString),
                                    leafBind('\'', innerChar),
                                    leafBind('`', innerGraveString),
                                    leafBind('w', innerWord),
                                    leafBind('W', innerWORD)),
                            transitionBind('a',
                                    leafBind('b', aBracket),
                                    leafBind('(', aBracket),
                                    leafBind(')', aBracket),
                                    leafBind('[', aSquareBracket),
                                    leafBind(']', aSquareBracket),
                                    leafBind('B', aBrace),
                                    leafBind('{', aBrace),
                                    leafBind('}', aBrace),
                                    leafBind('<', anAngleBrace),
                                    leafBind('>', anAngleBrace),
                                    leafBind('"', aString),
                                    leafBind('\'', aChar),
                                    leafBind('`', aGraveString),
                                    leafBind('w', aWord),
                                    leafBind('W', aWORD))),
                        new TextObjectState(motions()));

            textObjects = CountingState.wrap(textObjects);
        }
        return textObjects;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected State<Command> buildInitialState() {
        Command visualMode = new ChangeModeCommand(VisualMode.NAME);
        Command linewiseVisualMode = new ChangeModeCommand(LinewiseVisualMode.NAME);

        final Motion moveLeft = MoveLeft.INSTANCE;
        final Motion moveRight = MoveRight.INSTANCE;
        final Motion wordRight = MoveWordRight.INSTANCE;
        final Motion wordEndRight = MoveWordEndRight.INSTANCE;
        final Motion bol = LineStartMotion.NON_WHITESPACE;
        final Motion eol = new LineEndMotion(BorderPolicy.EXCLUSIVE);
        final Motion wholeLineEol = new LineEndMotion(BorderPolicy.LINE_WISE);

        final State<Motion> motions = motions();
        final TextObject wordForCW = new OptionDependentTextObject(Options.SANE_CW, wordRight, wordEndRight);
        final TextObject toEol = new MotionTextObject(eol);
        final TextObject toEolForY = new OptionDependentTextObject(Options.SANE_Y, eol, wholeLineEol);

        State<TextObject> textObjects = textObjects();
        State<TextObject> textObjectsForChange = union(state(leafBind('w', wordForCW)), textObjects);
        textObjectsForChange = CountingState.wrap(textObjectsForChange);

        TextOperation delete = DeleteOperation.INSTANCE;
        TextOperation change = ChangeOperation.INSTANCE;
        TextOperation yank   = YankOperation.INSTANCE;
        Command undo = UndoCommand.INSTANCE;
        Command redo = RedoCommand.INSTANCE;
        Command pasteAfter  = PasteAfterCommand.INSTANCE;
        Command pasteBefore = PasteBeforeCommand.INSTANCE;
        Command deleteNext = new TextOperationTextObjectCommand(delete, new MotionTextObject(moveRight));
        Command deletePrevious = new TextOperationTextObjectCommand(delete, new MotionTextObject(moveLeft));
        Command repeatLastOne = DotCommand.INSTANCE;
        Command tildeCmd = SwapCaseCommand.INSTANCE;
        Command stickToEOL = StickToEOLCommand.INSTANCE;
        LineEndMotion lineEndMotion = new LineEndMotion(BorderPolicy.LINE_WISE);
        Command substituteLine = new TextOperationTextObjectCommand(change, new MotionTextObject(lineEndMotion));
        Command substituteChar = new TextOperationTextObjectCommand(change, new MotionTextObject(moveRight));
        Command centerLine = CenterLineCommand.INSTANCE;
        
        Command afterEnteringVisualInc = new OptionDependentCommand<String>(Options.SELECTION, "inclusive",
                new VisualMotionCommand(moveRight));
        Command afterEnteringVisualExc = new OptionDependentCommand<String>(Options.SELECTION, "exclusive",
                new CountIgnoringNonRepeatableCommand() {
                    public void execute(EditorAdaptor editorAdaptor) throws CommandExecutionException {
                        Position position = editorAdaptor.getPosition();
                        editorAdaptor.setSelection(new  SimpleSelection(new StartEndTextRange(position, position)));
                    }
                });
        Command afterEnteringVisual = seq(afterEnteringVisualInc, afterEnteringVisualExc);

        State<Command> motionCommands = new GoThereState(motions);

        State<Command> platformSpecificState = getPlatformSpecificState(NAME);
        return RegisterState.wrap(CountingState.wrap(union(
                platformSpecificState,
                operatorCmdsWithUpperCase('d', delete, toEol,     textObjects),
                operatorCmdsWithUpperCase('y', yank,   toEolForY, textObjects),
                operatorCmdsWithUpperCase('c', change, toEol,     textObjectsForChange),
                state(leafBind('$', stickToEOL)),
                motionCommands,
                state(
                        leafBind('i', (Command) new ChangeToInsertModeCommand()),
                        leafBind('a', (Command) new ChangeToInsertModeCommand(new MotionCommand(moveRight))),
                        leafBind('I', (Command) new ChangeToInsertModeCommand(new MotionCommand(bol))),
                        leafBind('A', (Command) new ChangeToInsertModeCommand(new MotionCommand(eol))),
                        leafBind(':', (Command) new ChangeModeCommand(CommandLineMode.NAME)),
                        leafBind('?', (Command) new ChangeModeCommand(SearchMode.NAME, SearchMode.Direction.BACKWARD)),
                        leafBind('/', (Command) new ChangeModeCommand(SearchMode.NAME, SearchMode.Direction.FORWARD)),
                        leafBind('R', (Command) new ChangeModeCommand(ReplaceMode.NAME)),
                        leafBind('o', (Command) new ChangeToInsertModeCommand(InsertLineCommand.POST_CURSOR)),
                        leafBind('O', (Command) new ChangeToInsertModeCommand(InsertLineCommand.PRE_CURSOR)),
                        leafBind('v', seq(visualMode, afterEnteringVisual)),
                        leafBind('V', seq(linewiseVisualMode, new LinewiseVisualMotionCommand(moveRight))),
                        leafBind('p', pasteAfter),
                        leafBind('.', repeatLastOne),
                        leafBind('P', pasteBefore),
                        leafBind('x', deleteNext),
                        leafBind(SpecialKey.DELETE, deleteNext),
                        leafBind('X', deletePrevious),
                        leafBind('~', tildeCmd),
                        leafBind('S', substituteLine),
                        leafBind('s', substituteChar),
                        transitionBind('q',
                                convertKeyStroke(
                                        RecordMacroCommand.KEYSTROKE_CONVERTER,
                                        VimConstants.PRINTABLE_KEYSTROKES)),
                        transitionBind('@',
                                convertKeyStroke(
                                        PlaybackMacroCommand.KEYSTROKE_CONVERTER,
                                        VimConstants.PRINTABLE_KEYSTROKES)),
                        transitionBind('r', changeCaret(CaretType.UNDERLINE),
                                convertKeyStroke(
                                        ReplaceCommand.KEYSTROKE_CONVERTER,
                                        VimConstants.PRINTABLE_KEYSTROKES_WITH_NL)),
                        transitionBind('m',
                                convertKeyStroke(
                                        SetMarkCommand.KEYSTROKE_CONVERTER,
                                        VimConstants.PRINTABLE_KEYSTROKES)),
                        leafBind('u', undo),
                        leafCtrlBind('r', redo),
                        transitionBind('z',
                                leafBind('z', centerLine))))));
    }

    @Override
    protected void placeCursor() {
        Position pos = editorAdaptor.getPosition();
        int offset = pos.getViewOffset();
        LineInformation line = editorAdaptor.getViewContent().getLineInformationOfOffset(offset);
        if (isEnabled && line.getEndOffset() == offset && line.getLength() > 0) {
            editorAdaptor.setPosition(pos.addViewOffset(-1), false);
        }
    }

    @Override
    protected void commandDone() {
        super.commandDone();
        editorAdaptor.getCursorService().setCaret(CaretType.RECTANGULAR);
        editorAdaptor.getRegisterManager().activateDefaultRegister();
    }

    public void enterMode(ModeSwitchHint... args) {
        if (isEnabled) {
            return;
        }
        isEnabled = true;
        placeCursor();
        editorAdaptor.getCursorService().setCaret(CaretType.RECTANGULAR);
    }

    @Override
    public void leaveMode() {
        super.leaveMode();
        if (!isEnabled) {
            return;
        }
        isEnabled = false;
    }

    public String getName() {
        return NAME;
    }
}
