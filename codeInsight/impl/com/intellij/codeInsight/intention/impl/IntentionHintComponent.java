package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.RowIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author max
 * @author Mike
 * @author Valentin
 * @author Eugene Belyaev
 */
public class IntentionHintComponent extends JPanel {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.codeInsight.intention.impl.IntentionHintComponent.ListPopupRunnable");

  private static final Icon ourIntentionIcon = IconLoader.getIcon("/actions/intentionBulb.png");
  private static final Icon ourQuickFixIcon = IconLoader.getIcon("/actions/quickfixBulb.png");
  private static final Icon ourIntentionOffIcon = IconLoader.getIcon("/actions/intentionOffBulb.png");
  private static final Icon ourQuickFixOffIcon = IconLoader.getIcon("/actions/quickfixOffBulb.png");
  private static final Icon ourArrowIcon = IconLoader.getIcon("/general/arrowDown.png");
  private static final Border INACTIVE_BORDER = null;
  private static final Insets INACTIVE_MARGIN = new Insets(0, 0, 0, 0);
  private static final Insets ACTIVE_MARGIN = new Insets(0, 0, 0, 0);

  private final Project myProject;
  private final Editor myEditor;

  private static Alarm myAlarm = new Alarm();

  private RowIcon myHighlightedIcon;
  private JButton myButton;

  private final Icon mySmartTagIcon;

  private static final int DELAY = 500;
  private MyComponentHint myComponentHint;
  private static final Color BACKGROUND_COLOR = new Color(255, 255, 255, 0);
  private boolean myPopupShown = false;
  private ListPopup myPopup;

  private class IntentionListStep implements ListPopupStep<IntentionActionWithTextCaching> {
    private List<IntentionActionWithTextCaching> myActions;
    private IntentionManagerSettings mySettings;
    private List<IntentionAction> myQuickFixes;

    public IntentionListStep(ArrayList<Pair<IntentionAction, List<IntentionAction>>> quickFixes,
                             ArrayList<Pair<IntentionAction, List<IntentionAction>>> intentions) {
      mySettings = IntentionManagerSettings.getInstance();
      List<Pair<IntentionAction, List<IntentionAction>>> allActions = new ArrayList<Pair<IntentionAction, List<IntentionAction>>>(quickFixes);
      allActions.addAll(intentions);
      List<IntentionAction> actions = new ArrayList<IntentionAction>();
      for (Pair<IntentionAction, List<IntentionAction>> pair : quickFixes) {
        actions.add(pair.first);
        if (pair.second != null) {
          actions.addAll(pair.second);
        }
      }
      myQuickFixes = actions;
      myActions = Arrays.asList(wrapActions(allActions));
    }

    private IntentionActionWithTextCaching[] wrapActions(List<Pair<IntentionAction, List<IntentionAction>>> actions) {
      IntentionActionWithTextCaching [] compositeActions = new IntentionActionWithTextCaching[actions.size()];
      int index = 0;
      for (Pair<IntentionAction, List<IntentionAction>> pair : actions) {
        if (pair.first != null) {
          IntentionActionWithTextCaching action = new IntentionActionWithTextCaching(pair.first);
          if (pair.second != null) {
            for (IntentionAction intentionAction : pair.second) {
              action.addAction(intentionAction, myQuickFixes.contains(intentionAction));
            }
          }
          compositeActions[index ++] = action;
        }
      }
      return compositeActions;
    }

    public String getTitle() {
      return null;
    }

    public boolean isSelectable(final IntentionActionWithTextCaching action) {
      return true;
    }

    public PopupStep onChosen(final IntentionActionWithTextCaching action, final boolean finalChoice) {
      if (finalChoice && !(action.getAction() instanceof EmptyIntentionAction)) {
        applyAction(action.getAction());
        return PopupStep.FINAL_CHOICE;
      }

      if (hasSubstep(action)) {
        return getSubStep(action);
      }

      return FINAL_CHOICE;
    }

    private PopupStep getSubStep(final IntentionActionWithTextCaching action) {
      final ArrayList<Pair<IntentionAction, List<IntentionAction>>> intentions = new ArrayList<Pair<IntentionAction, List<IntentionAction>>>();
      final List<IntentionAction> optionIntentions = action.getOptionIntentions();
      for (final IntentionAction optionIntention : optionIntentions) {
        intentions.add(new Pair<IntentionAction, List<IntentionAction>>(optionIntention, null));
      }
      final ArrayList<Pair<IntentionAction, List<IntentionAction>>> quickFixes = new ArrayList<Pair<IntentionAction, List<IntentionAction>>>();
      final List<IntentionAction> optionFixes = action.getOptionFixes();
      for (final IntentionAction optionFix : optionFixes) {
        quickFixes.add(new Pair<IntentionAction, List<IntentionAction>>(optionFix, null));
      }

      return new IntentionListStep(quickFixes, intentions);
    }

    public boolean hasSubstep(final IntentionActionWithTextCaching action) {
      return action.getOptionIntentions().size() + action.getOptionFixes().size() > 0;
    }

    public List<IntentionActionWithTextCaching> getValues() {
      return myActions;
    }

    @NotNull
    public String getTextFor(final IntentionActionWithTextCaching action) {
      return action.getText();
    }

    public Icon getIconFor(final IntentionActionWithTextCaching value) {
      final IntentionAction action = value.getAction();

      if (mySettings.isShowLightBulb(action)) {
        if (myQuickFixes.contains(action)) {
          return ourQuickFixIcon;
        }
        else {
          return ourIntentionIcon;
        }
      }
      else {
        if (myQuickFixes.contains(action)) {
          return ourQuickFixOffIcon;
        }
        else {
          return ourIntentionOffIcon;
        }
      }
    }

    public void canceled() {
      if (myPopup.getListStep() == this) {
        // Root canceled. Create new popup. This one cannot be reused.
        myPopup = JBPopupFactory.getInstance().createWizardStep(this);
      }
    }

    public int getDefaultOptionIndex() { return 0; }
    public ListSeparator getSeparatorAbove(final IntentionActionWithTextCaching value) { return null; }
    public boolean isMnemonicsNavigationEnabled() { return false; }
    public MnemonicNavigationFilter<IntentionActionWithTextCaching> getMnemonicNavigationFilter() { return null; }
    public boolean isSpeedSearchEnabled() { return false; }
    public boolean isAutoSelectionEnabled() { return false; }
    public SpeedSearchFilter<IntentionActionWithTextCaching> getSpeedSearchFilter() { return null; }
  }

  private static class IntentionActionWithTextCaching {
    private ArrayList<IntentionAction> myOptionIntentions;
    private ArrayList<IntentionAction> myOptionFixes;
    private String myText = null;
    private IntentionAction myAction;

    public IntentionActionWithTextCaching(IntentionAction action) {
      myOptionIntentions = new ArrayList<IntentionAction>();
      myOptionFixes = new ArrayList<IntentionAction>();
      myText = action.getText();
      myAction = action;
    }

    String getText() {
      return myText;
    }

    public void addAction(final IntentionAction action, boolean isFix) {
      if (isFix) {
        myOptionFixes.add(action);
      }
      else {
        myOptionIntentions.add(action);
      }
    }

    public IntentionAction getAction() {
      return myAction;
    }

    public List<IntentionAction> getOptionIntentions() {
      return myOptionIntentions;
    }

    public List<IntentionAction> getOptionFixes() {
      return myOptionFixes;
    }
  }

  public static IntentionHintComponent showIntentionHint(Project project,
                                                         Editor view,
                                                         ArrayList<Pair<IntentionAction, List<IntentionAction>>> intentions,
                                                         ArrayList<Pair<IntentionAction, List<IntentionAction>>> quickFixes,
                                                         boolean showExpanded) {
    final IntentionHintComponent component = new IntentionHintComponent(project, view, intentions, quickFixes);

    if (showExpanded) {
      component.showIntentionHintImpl(false);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          component.showPopup();
        }
      });
    }
    else {
      component.showIntentionHintImpl(true);
    }

    return component;
  }

  public void updateIfNotShowingPopup(ArrayList<Pair<IntentionAction, List<IntentionAction>>> quickfixes,
                                      ArrayList<Pair<IntentionAction, List<IntentionAction>>> intentions) {
    if (!myPopupShown) {
      myPopup = JBPopupFactory.getInstance().createWizardStep(new IntentionListStep(quickfixes, intentions));
    }
  }

  private void showIntentionHintImpl(final boolean delay) {
    final int offset = myEditor.getCaretModel().getOffset();
    final HintManager hintManager = HintManager.getInstance();

    myComponentHint.setShouldDelay(delay);

    hintManager.showQuestionHint(myEditor,
                                 getHintPosition(myEditor, offset),
                                 offset,
                                 offset,
                                 myComponentHint,
                                 new QuestionAction() {
                                   public boolean execute() {
                                     showPopup();
                                     return true;
                                   }
                                 });
  }

  private static Point getHintPosition(Editor editor, int offset) {
    final LogicalPosition pos = editor.offsetToLogicalPosition(offset);
    int line = pos.line;


    Point location;
    final Point position = editor.logicalPositionToXY(new LogicalPosition(line, 0));
    final int yShift = (ourIntentionIcon.getIconHeight() - editor.getLineHeight() - 1) / 2 - 1;

    LOG.assertTrue(editor.getComponent().isDisplayable());
    location = SwingUtilities.convertPoint(editor.getContentComponent(),
                                           new Point(editor.getScrollingModel().getVisibleArea().x,
                                                     position.y + yShift),
                                           editor.getComponent().getRootPane().getLayeredPane());

    return new Point(location.x, location.y);
  }

  public IntentionHintComponent(Project project,
                                Editor editor,
                                ArrayList<Pair<IntentionAction, List<IntentionAction>>> intentions,
                                ArrayList<Pair<IntentionAction, List<IntentionAction>>> quickFixes) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myProject = project;
    myEditor = editor;

    setLayout(new BorderLayout());
    setOpaque(false);

    boolean showFix = false;
    for (final Pair<IntentionAction, List<IntentionAction>> pairs : quickFixes) {
      IntentionAction fix = pairs.first;
      if (IntentionManagerSettings.getInstance().isShowLightBulb(fix)) {
        showFix = true;
        break;
      }
    }
    mySmartTagIcon = showFix ? ourQuickFixIcon : ourIntentionIcon;

    myHighlightedIcon = new RowIcon(2);
    myHighlightedIcon.setIcon(mySmartTagIcon, 0);
    myHighlightedIcon.setIcon(ourArrowIcon, 1);

    myButton = new JButton(mySmartTagIcon);
    myButton.setFocusable(false);
    myButton.setMargin(INACTIVE_MARGIN);
    myButton.setBorderPainted(false);
    myButton.setContentAreaFilled(false);

    add(myButton, BorderLayout.CENTER);
    setBorder(INACTIVE_BORDER);

    myButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        showPopup();
      }
    });

    myButton.addMouseListener(new MouseAdapter() {
      public void mouseEntered(MouseEvent e) {
        onMouseEnter();
      }

      public void mouseExited(MouseEvent e) {
        onMouseExit();
      }
    });

    myComponentHint = new MyComponentHint(this);
    myPopup = JBPopupFactory.getInstance().createWizardStep(new IntentionListStep(quickFixes, intentions));
  }

  private void onMouseExit() {
    Window ancestor = SwingUtilities.getWindowAncestor(myPopup.getContent());
    if (ancestor == null) {
      myButton.setBackground(BACKGROUND_COLOR);
      myButton.setIcon(mySmartTagIcon);
      setBorder(INACTIVE_BORDER);
      myButton.setMargin(INACTIVE_MARGIN);
      updateComponentHintSize();
    }
  }

  private void onMouseEnter() {
    myButton.setBackground(HintUtil.QUESTION_COLOR);
    myButton.setIcon(myHighlightedIcon);
    setBorder(BorderFactory.createLineBorder(Color.black));
    myButton.setMargin(ACTIVE_MARGIN);
    updateComponentHintSize();

    String acceleratorsText = KeymapUtil.getFirstKeyboardShortcutText(
      ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
    if (acceleratorsText.length() > 0) {
      myButton.setToolTipText(CodeInsightBundle.message("lightbulb.tooltip", acceleratorsText));
    }
  }

  private void updateComponentHintSize() {
    Component component = myComponentHint.getComponent();
    component.setSize(getPreferredSize().width, getHeight());
  }

  public void closePopup() {
    if (myPopupShown) {
      myPopup.cancel();
      myPopupShown = false;
    }
  }

  public void showPopup() {
    if (isShowing()) {
      myPopup.show(RelativePoint.getSouthWestOf(this));
    }
    else {
      myPopup.showInBestPositionFor(myEditor);
    }

    myPopupShown = true;
  }

  private class MyComponentHint extends LightweightHint {
    private boolean myVisible = false;
    private boolean myShouldDelay;

    public MyComponentHint(JComponent component) {
      super(component);
    }

    public void show(final JComponent parentComponent, final int x, final int y, final JComponent focusBackComponent) {
      myVisible = true;
      if (myShouldDelay) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable() {
          public void run() {
            showImpl(parentComponent, x, y, focusBackComponent);
          }
        }, DELAY);
      }
      else {
        showImpl(parentComponent, x, y, focusBackComponent);
      }
    }

    private void showImpl(JComponent parentComponent, int x, int y, JComponent focusBackComponent) {
      if (!parentComponent.isShowing()) return;
      super.show(parentComponent, x, y, focusBackComponent);
    }

    public void hide() {
      myVisible = false;
      myAlarm.cancelAllRequests();
      super.hide();
    }

    public boolean isVisible() {
      return myVisible || super.isVisible();
    }

    public void setShouldDelay(boolean shouldDelay) {
      myShouldDelay = shouldDelay;
    }
  }

  private void applyAction(final IntentionAction action) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        HintManager hintManager = HintManager.getInstance();
        hintManager.hideAllHints();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
            PsiDocumentManager.getInstance(myProject).commitAllDocuments();

            if (action.isAvailable(myProject, myEditor, file)) {
              Runnable runnable = new Runnable() {
                public void run() {
                  try {
                    action.invoke(myProject, myEditor, file);
                    DaemonCodeAnalyzer.getInstance(myProject).updateVisibleHighlighters(myEditor);
                  }
                  catch (IncorrectOperationException e1) {
                    LOG.error(e1);
                  }
                }
              };

              if (action.startInWriteAction()) {
                final Runnable _runnable = runnable;
                runnable = new Runnable() {
                  public void run() {
                    ApplicationManager.getApplication().runWriteAction(_runnable);
                  }
                };
              }

              CommandProcessor.getInstance().executeCommand(myProject, runnable, action.getText(), null);
            }
          }
        });
      }
    });
  }

  public static class EnableDisableIntentionAction implements IntentionAction{
    private String myActionFamilyName;
    private IntentionManagerSettings mySettings = IntentionManagerSettings.getInstance();

    public EnableDisableIntentionAction(IntentionAction action) {
      myActionFamilyName = action.getFamilyName();
    }

    public String getText() {
      return mySettings.isEnabled(myActionFamilyName) ?
             CodeInsightBundle.message("disable.intention.action", myActionFamilyName) :
             CodeInsightBundle.message("enable.intention.action", myActionFamilyName);
    }

    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      mySettings.setEnabled(myActionFamilyName, !mySettings.isEnabled(myActionFamilyName));
    }

    public boolean startInWriteAction() {
      return false;
    }
  }
}
