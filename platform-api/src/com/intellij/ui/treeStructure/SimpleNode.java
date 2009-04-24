/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.treeStructure;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.ComparableObjectCheck;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;

public abstract class SimpleNode extends PresentableNodeDescriptor implements ComparableObject {

  protected static final SimpleNode[] NO_CHILDREN = new SimpleNode[0];

  protected SimpleNode(Project project) {
    this(project, null);
  }

  protected SimpleNode(Project project, NodeDescriptor parentDescriptor) {
    super(project, parentDescriptor);
    myName = "";
  }

  protected SimpleNode(SimpleNode parent) {
    this(parent == null ? null : parent.myProject, parent);
  }

  protected SimpleNode() {
    super(null, null);
  }

  public String toString() {
    return getName();
  }

  public int getWeight() {
    return 10;
  }

  protected SimpleTextAttributes getErrorAttributes() {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, getColor(), Color.red);
  }

  protected SimpleTextAttributes getPlainAttributes() {
    return new SimpleTextAttributes(Font.PLAIN, getColor());
  }

  private FileStatus getFileStatus() {
    return FileStatus.NOT_CHANGED;
  }

  @Nullable
  protected Object updateElement() {
    return getElement();
  }

  protected void update(PresentationData presentation) {
    Object newElement = updateElement();
    boolean changed = false;
    if (getElement() != newElement) {
      presentation.setChanged(true);
    }
    if (newElement == null) return;

    Color oldColor = myColor;
    String oldName = myName;
    Icon oldOpenIcon = myOpenIcon;
    Icon oldClosedIcon = myClosedIcon;
    List<ColoredFragment> oldFragments = new ArrayList<ColoredFragment>(presentation.getColoredText());

    myColor = Color.black;
    updateFileStatus();

    doUpdate();

    myName = getName();
    presentation.setPresentableText(myName);

    presentation.setChanged(changed ||
                            !Comparing.equal(new Object[]{myOpenIcon, myClosedIcon, myName, oldFragments, myColor},
                                             new Object[]{oldOpenIcon, oldClosedIcon, oldName, oldFragments, oldColor}));

    presentation.setForcedTextForeground(myColor);
    presentation.setOpenIcon(myOpenIcon);
    presentation.setClosedIcon(myClosedIcon);
  }

  protected void updateFileStatus() {
    assert getFileStatus() != null : getClass().getName() + ' ' + toString();

    Color fileStatusColor = getFileStatus().getColor();
    if (fileStatusColor != null) {
      myColor = fileStatusColor;
    }
  }

  /**
   * @deprecated use {@link #getTemplatePresenation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(com.intellij.ide.projectView.PresentationData)}
   */
  public final void setNodeText(String text, String tooltip, boolean hasError) {
    clearColoredText();
    SimpleTextAttributes attributes = hasError ? getErrorAttributes() : getPlainAttributes();
    getTemplatePresenation().addText(new ColoredFragment(text, tooltip, attributes));
  }

  /**
   * @deprecated use {@link #getTemplatePresenation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(com.intellij.ide.projectView.PresentationData)}
   */
  public final void setPlainText(String aText) {
    clearColoredText();
    addPlainText(aText);
  }

  /**
   * @deprecated use {@link #getTemplatePresenation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(com.intellij.ide.projectView.PresentationData)}
   */
  public final void addPlainText(String aText) {
    getTemplatePresenation().addText(new ColoredFragment(aText, getPlainAttributes()));
  }

  /**
   * @deprecated use {@link #getTemplatePresenation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(com.intellij.ide.projectView.PresentationData)}
   */
  public final void addErrorText(String aText, String errorTooltipText) {
    getTemplatePresenation().addText(new ColoredFragment(aText, errorTooltipText, getErrorAttributes()));
  }

  /**
   * @deprecated use {@link #getTemplatePresenation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(com.intellij.ide.projectView.PresentationData)}
   */
  public final void clearColoredText() {
    getTemplatePresenation().clearText();
  }

  /**
   * @deprecated use {@link #getTemplatePresenation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(com.intellij.ide.projectView.PresentationData)}
   */
  public final void addColoredFragment(String aText, SimpleTextAttributes aAttributes) {
    addColoredFragment(aText, null, aAttributes);
  }

  /**
   * @deprecated use {@link #getTemplatePresenation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(com.intellij.ide.projectView.PresentationData)}
   */
  public final void addColoredFragment(String aText, String toolTip, SimpleTextAttributes aAttributes) {
    getTemplatePresenation().addText(new ColoredFragment(aText, toolTip, aAttributes));
  }

  /**
   * @deprecated use {@link #getTemplatePresenation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(com.intellij.ide.projectView.PresentationData)}
   */
  public final void addColoredFragment(ColoredFragment fragment) {
    getTemplatePresenation().addText(new ColoredFragment(fragment.getText(), fragment.getAttributes()));
  }

  protected void doUpdate() {
  }

  public Object getElement() {
    return this;
  }

  public final SimpleNode getParent() {
    return (SimpleNode)getParentDescriptor();
  }

  public int getIndex(SimpleNode child) {
    final SimpleNode[] kids = getChildren();
    for (int i = 0; i < kids.length; i++) {
      SimpleNode each = kids[i];
      if (each.equals(child)) return i;
    }

    return -1;
  }

  public abstract SimpleNode[] getChildren();

  public void accept(SimpleNodeVisitor visitor) {
    visitor.accept(this);
  }

  public void handleSelection(SimpleTree tree) {
  }

  public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
  }

  public boolean isAlwaysShowPlus() {
    return false;
  }

  public boolean isAutoExpandNode() {
    return false;
  }

  public boolean shouldHaveSeparator() {
    return false;
  }

  /**
   * @deprecated use {@link #getTemplatePresenation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(com.intellij.ide.projectView.PresentationData)}
   */
  public void setUniformIcon(Icon aIcon) {
    setIcons(aIcon, aIcon);
  }

  /**
   * @deprecated use {@link #getTemplatePresenation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(com.intellij.ide.projectView.PresentationData)}
   */
  public final void setIcons(Icon aClosed, Icon aOpen) {
    myOpenIcon = aOpen;
    myClosedIcon = aClosed;
  }

  /**
   * @deprecated never called by Tree classes
   */
  public final ColoredFragment[] getColoredText() {
    final List<ColoredFragment> list = getTemplatePresenation().getColoredText();
    return list.toArray(new ColoredFragment[list.size()]);
  }

  public Object[] getEqualityObjects() {
    return NONE;
  }

  public boolean isContentHighlighted() {
    return false;
  }

  public boolean isHighlightableContentNode(final SimpleNode kid) {
    return true;
  }

  public int getChildCount() {
    return getChildren().length;
  }

  public SimpleNode getChildAt(final int i) {
    return getChildren()[i];
  }

  public boolean isParentOf(SimpleNode eachNode) {
    SimpleNode eachParent = eachNode.getParent();
    while (eachParent != null) {
      if (eachParent == this) return true;
      eachParent = eachParent.getParent();
    }
    return false;
  }


  public boolean isAncestorOrSelf(SimpleNode selectedNode) {
    SimpleNode node = selectedNode;
    while (node != null) {
      if (equals(node)) return true;
      node = node.getParent();
    }
    return false;
  }

  public final boolean equals(Object o) {
    return ComparableObjectCheck.equals(this, o);
  }

  public final int hashCode() {
    return ComparableObjectCheck.hashCode(this, super.hashCode());
  }
}
