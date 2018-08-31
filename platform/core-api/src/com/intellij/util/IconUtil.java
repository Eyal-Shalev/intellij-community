// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.icons.AllIcons;
import com.intellij.ide.FileIconPatcher;
import com.intellij.ide.FileIconProvider;
import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import com.intellij.ui.IconDeferrer;
import com.intellij.ui.JBColor;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
import com.intellij.util.ui.*;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.JBUI.ScaleContextAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RGBImageFilter;

import static com.intellij.util.ui.JBUI.ScaleType.USR_SCALE;
import static java.lang.Math.round;


/**
 * @author max
 * @author Konstantin Bulenkov
 */
public class IconUtil {
  private static final Key<Boolean> PROJECT_WAS_EVER_INITIALIZED = Key.create("iconDeferrer:projectWasEverInitialized");

  private static boolean wasEverInitialized(@NotNull Project project) {
    Boolean was = project.getUserData(PROJECT_WAS_EVER_INITIALIZED);
    if (was == null) {
      if (project.isInitialized()) {
        was = true;
        project.putUserData(PROJECT_WAS_EVER_INITIALIZED, true);
      }
      else {
        was = false;
      }
    }

    return was.booleanValue();
  }

  @NotNull
  public static Icon cropIcon(@NotNull Icon icon, int maxWidth, int maxHeight) {
    if (icon.getIconHeight() <= maxHeight && icon.getIconWidth() <= maxWidth) {
      return icon;
    }

    Image image = toImage(icon);
    if (image == null) return icon;

    double scale = 1f;
    if (image instanceof JBHiDPIScaledImage) {
      scale = ((JBHiDPIScaledImage)image).getScale();
      image = ((JBHiDPIScaledImage)image).getDelegate();
    }
    BufferedImage bi = ImageUtil.toBufferedImage(image);
    final Graphics2D g = bi.createGraphics();

    int imageWidth = ImageUtil.getRealWidth(image);
    int imageHeight = ImageUtil.getRealHeight(image);

    maxWidth = maxWidth == Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)round(maxWidth * scale);
    maxHeight = maxHeight == Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)round(maxHeight * scale);
    final int w = Math.min(imageWidth, maxWidth);
    final int h = Math.min(imageHeight, maxHeight);

    final BufferedImage img = UIUtil.createImage(g, w, h, Transparency.TRANSLUCENT);
    final int offX = imageWidth > maxWidth ? (imageWidth - maxWidth) / 2 : 0;
    final int offY = imageHeight > maxHeight ? (imageHeight - maxHeight) / 2 : 0;
    for (int col = 0; col < w; col++) {
      for (int row = 0; row < h; row++) {
        img.setRGB(col, row, bi.getRGB(col + offX, row + offY));
      }
    }
    g.dispose();
    return new JBImageIcon(RetinaImage.createFrom(img, scale, null));
  }

  @NotNull
  public static Icon cropIcon(@NotNull Icon icon, @NotNull Rectangle area) {
    if (!new Rectangle(icon.getIconWidth(), icon.getIconHeight()).contains(area)) {
      return icon;
    }
    return new CropIcon(icon, area);
  }

  @NotNull
  public static Icon flip(@NotNull final Icon icon, final boolean horizontal) {
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D)g.create();
        try {
          AffineTransform transform =
            AffineTransform.getTranslateInstance(horizontal ? x + getIconWidth() : x, horizontal ? y : y + getIconHeight());
          transform.concatenate(AffineTransform.getScaleInstance(horizontal ? -1 : 1, horizontal ? 1 : -1));
          transform.preConcatenate(g2d.getTransform());
          g2d.setTransform(transform);
          icon.paintIcon(c, g2d, 0, 0);
        }
        finally {
          g2d.dispose();
        }
      }

      @Override
      public int getIconWidth() {
        return icon.getIconWidth();
      }

      @Override
      public int getIconHeight() {
        return icon.getIconHeight();
      }
    };
  }

  private static final NullableFunction<FileIconKey, Icon> ICON_NULLABLE_FUNCTION = key -> {
    final VirtualFile file = key.getFile();
    final int flags = filterFileIconFlags(file, key.getFlags());
    final Project project = key.getProject();

    if (!file.isValid() || project != null && (project.isDisposed() || !wasEverInitialized(project))) return null;

    final Icon providersIcon = getProvidersIcon(file, flags, project);
    Icon icon = providersIcon == null ? VirtualFilePresentation.getIconImpl(file) : providersIcon;

    final boolean dumb = project != null && DumbService.getInstance(project).isDumb();
    for (FileIconPatcher patcher : getPatchers()) {
      if (dumb && !DumbService.isDumbAware(patcher)) {
        continue;
      }

      // render without locked icon patch since we are going to apply it later anyway
      icon = patcher.patchIcon(icon, file, flags & ~Iconable.ICON_FLAG_READ_STATUS, project);
    }

    if (file.is(VFileProperty.SYMLINK)) {
      icon = new LayeredIcon(icon, PlatformIcons.SYMLINK_ICON);
    }
    if (BitUtil.isSet(flags, Iconable.ICON_FLAG_READ_STATUS) &&
        (!file.isWritable() || !WritingAccessProvider.isPotentiallyWritable(file, project))) {
      icon = new LayeredIcon(icon, PlatformIcons.LOCKED_ICON);
    }

    Iconable.LastComputedIcon.put(file, icon, flags);

    return icon;
  };

  @Iconable.IconFlags
  private static int filterFileIconFlags(@NotNull VirtualFile file, @Iconable.IconFlags int flags) {
    UserDataHolder fileTypeDataHolder = ObjectUtils.tryCast(file.getFileType(), UserDataHolder.class);
    int fileTypeFlagIgnoreMask = Iconable.ICON_FLAG_IGNORE_MASK.get(fileTypeDataHolder, 0);
    int flagIgnoreMask = Iconable.ICON_FLAG_IGNORE_MASK.get(file, fileTypeFlagIgnoreMask);
    //noinspection MagicConstant
    return flags & ~flagIgnoreMask;
  }

  public static Icon getIcon(@NotNull final VirtualFile file, @Iconable.IconFlags final int flags, @Nullable final Project project) {
    Icon lastIcon = Iconable.LastComputedIcon.get(file, flags);

    final Icon base = lastIcon != null ? lastIcon : VirtualFilePresentation.getIconImpl(file);
    return IconDeferrer.getInstance().defer(base, new FileIconKey(file, project, flags), ICON_NULLABLE_FUNCTION);
  }

  @Nullable
  private static Icon getProvidersIcon(@NotNull VirtualFile file, @Iconable.IconFlags int flags, Project project) {
    for (FileIconProvider provider : getProviders()) {
      final Icon icon = provider.getIcon(file, flags, project);
      if (icon != null) return icon;
    }
    return null;
  }

  @NotNull
  public static Icon getEmptyIcon(boolean showVisibility) {
    RowIcon baseIcon = new RowIcon(2);
    baseIcon.setIcon(createEmptyIconLike(PlatformIcons.CLASS_ICON_PATH), 0);
    if (showVisibility) {
      baseIcon.setIcon(createEmptyIconLike(PlatformIcons.PUBLIC_ICON_PATH), 1);
    }
    return baseIcon;
  }

  @NotNull
  private static Icon createEmptyIconLike(@NotNull String baseIconPath) {
    Icon baseIcon = IconLoader.findIcon(baseIconPath);
    if (baseIcon == null) {
      return EmptyIcon.ICON_16;
    }
    return EmptyIcon.create(baseIcon);
  }

  private static class FileIconProviderHolder {
    private static final FileIconProvider[] myProviders = Extensions.getExtensions(FileIconProvider.EP_NAME);
  }

  @NotNull
  private static FileIconProvider[] getProviders() {
    return FileIconProviderHolder.myProviders;
  }

  private static class FileIconPatcherHolder {
    private static final FileIconPatcher[] ourPatchers = Extensions.getExtensions(FileIconPatcher.EP_NAME);
  }

  @NotNull
  private static FileIconPatcher[] getPatchers() {
    return FileIconPatcherHolder.ourPatchers;
  }

  public static Image toImage(@NotNull Icon icon) {
    return IconLoader.toImage(icon);
  }

  @NotNull
  public static Icon getAddIcon() {
    return AllIcons.General.Add;
  }

  @NotNull
  public static Icon getRemoveIcon() {
    return AllIcons.General.Remove;
  }

  @NotNull
  public static Icon getMoveUpIcon() {
    return AllIcons.Actions.MoveUp;
  }

  @NotNull
  public static Icon getMoveDownIcon() {
    return AllIcons.Actions.MoveDown;
  }

  @NotNull
  public static Icon getEditIcon() {
    return AllIcons.Actions.Edit;
  }

  @NotNull
  public static Icon getAddClassIcon() {
    return getToolbarDecoratorIcon("addClass.png");
  }

  @NotNull
  public static Icon getAddPatternIcon() {
    return getToolbarDecoratorIcon("addPattern.png");
  }

  @NotNull
  public static Icon getAddJiraPatternIcon() {
    return getToolbarDecoratorIcon("addJira.png");
  }

  @NotNull
  public static Icon getAddYouTrackPatternIcon() {
    return getToolbarDecoratorIcon("addYouTrack.png");
  }

  @NotNull
  public static Icon getAddBlankLineIcon() {
    return getToolbarDecoratorIcon("addBlankLine.png");
  }

  @NotNull
  public static Icon getAddPackageIcon() {
    return getToolbarDecoratorIcon("addPackage.png");
  }

  @NotNull
  public static Icon getAddLinkIcon() {
    return getToolbarDecoratorIcon("addLink.png");
  }

  @NotNull
  public static Icon getAddFolderIcon() {
    return getToolbarDecoratorIcon("addFolder.png");
  }

  @NotNull
  public static Icon getAnalyzeIcon() {
    return getToolbarDecoratorIcon("analyze.png");
  }

  public static void paintInCenterOf(@NotNull Component c, @NotNull Graphics g, @NotNull Icon icon) {
    final int x = (c.getWidth() - icon.getIconWidth()) / 2;
    final int y = (c.getHeight() - icon.getIconHeight()) / 2;
    icon.paintIcon(c, g, x, y);
  }

  @NotNull
  private static Icon getToolbarDecoratorIcon(@NotNull String name) {
    return IconLoader.getIcon(getToolbarDecoratorIconsFolder() + name);
  }

  @NotNull
  private static String getToolbarDecoratorIconsFolder() {
    return "/toolbarDecorator/" + (SystemInfo.isMac ? "mac/" : "");
  }

  /**
   * Result icons look like original but have equal (maximum) size
   */
  @NotNull
  public static Icon[] getEqualSizedIcons(@NotNull Icon... icons) {
    Icon[] result = new Icon[icons.length];
    int width = 0;
    int height = 0;
    for (Icon icon : icons) {
      width = Math.max(width, icon.getIconWidth());
      height = Math.max(height, icon.getIconHeight());
    }
    for (int i = 0; i < icons.length; i++) {
      result[i] = new IconSizeWrapper(icons[i], width, height);
    }
    return result;
  }

  @NotNull
  public static Icon toSize(@Nullable Icon icon, int width, int height) {
    return new IconSizeWrapper(icon, width, height);
  }

  public static class IconSizeWrapper implements Icon {
    public final Icon myIcon;
    private final int myWidth;
    private final int myHeight;

    protected IconSizeWrapper(@Nullable Icon icon, int width, int height) {
      myIcon = icon;
      myWidth = width;
      myHeight = height;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      paintIcon(myIcon, c, g, x, y);
    }

    protected void paintIcon(@Nullable Icon icon, Component c, Graphics g, int x, int y) {
      if (icon == null) return;
      x += (myWidth - icon.getIconWidth()) / 2;
      y += (myHeight - icon.getIconHeight()) / 2;
      icon.paintIcon(c, g, x, y);
    }

    @Override
    public int getIconWidth() {
      return myWidth;
    }

    @Override
    public int getIconHeight() {
      return myHeight;
    }
  }

  private static class CropIcon implements Icon {
    private final Icon mySrc;
    private final Rectangle myCrop;

    private CropIcon(@NotNull Icon src, @NotNull Rectangle crop) {
      mySrc = src;
      myCrop = crop;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      mySrc.paintIcon(c, g, x - myCrop.x, y - myCrop.y);
    }

    @Override
    public int getIconWidth() {
      return myCrop.width;
    }

    @Override
    public int getIconHeight() {
      return myCrop.height;
    }
  }

  @NotNull
  public static Icon scale(@NotNull final Icon source, double _scale) {
    final int hiDPIScale;
    if (source instanceof ImageIcon) {
      Image image = ((ImageIcon)source).getImage();
      hiDPIScale = image instanceof JBHiDPIScaledImage ? 2 : 1;
    }
    else {
      hiDPIScale = 1;
    }
    final double scale = Math.min(32, Math.max(.1, _scale));
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D)g.create();
        try {
          g2d.translate(x, y);
          AffineTransform transform = AffineTransform.getScaleInstance(scale, scale);
          transform.preConcatenate(g2d.getTransform());
          g2d.setTransform(transform);
          g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
          source.paintIcon(c, g2d, 0, 0);
        }
        finally {
          g2d.dispose();
        }
      }

      @Override
      public int getIconWidth() {
        return (int)(source.getIconWidth() * scale) / hiDPIScale;
      }

      @Override
      public int getIconHeight() {
        return (int)(source.getIconHeight() * scale) / hiDPIScale;
      }
    };
  }

  /**
   * Returns a scaled icon instance.
   * <p>
   * The method delegates to {@link ScalableIcon#scale(float)} when applicable,
   * otherwise defaults to {@link #scale(Icon, double)}
   * <p>
   * In the following example:
   * <pre>
   * Icon myIcon = new MyIcon();
   * Icon scaledIcon = IconUtil.scale(myIcon, myComp, 2f);
   * Icon anotherScaledIcon = IconUtil.scale(scaledIcon, myComp, 2f);
   * assert(scaledIcon.getIconWidth() == anotherScaledIcon.getIconWidth()); // compare the scale of the icons
   * </pre>
   * The result of the assertion depends on {@code MyIcon} implementation. When {@code scaledIcon} is an instance of {@link ScalableIcon},
   * then {@code anotherScaledIcon} should be scaled according to the {@link ScalableIcon} javadoc, and the assertion should pass.
   * Otherwise, {@code anotherScaledIcon} should be 2 times bigger than {@code scaledIcon}, and 4 times bigger than {@code myIcon}.
   * So, prior to scale the icon recursively, the returned icon should be inspected for its type to understand the result.
   * But recursive scale should better be avoided.
   *
   * @param icon the icon to scale
   * @param ancestor the component (or its ancestor) painting the icon, or null when not available
   * @param scale the scale factor
   * @return the scaled icon
   */
  @NotNull
  public static Icon scale(@NotNull Icon icon, @Nullable Component ancestor, float scale) {
    if (icon instanceof ScalableIcon) {
      if (icon instanceof ScaleContextAware) {
        ((ScaleContextAware)icon).updateScaleContext(ancestor != null ? ScaleContext.create(ancestor) : null);
      }
      return ((ScalableIcon)icon).scale(scale);
    }
    return scale(icon, scale);
  }

  /**
   * Returns a scaled icon instance, in scale of the provided font size.
   * <p>
   * The method delegates to {@link ScalableIcon#scale(float)} when applicable,
   * otherwise defaults to {@link #scale(Icon, double)}
   * <p>
   * Refer to {@link #scale(Icon, Component, float)} for more details.
   *
   * @param icon the icon to scale
   * @param ancestor the component (or its ancestor) painting the icon, or null when not available
   * @param fontSize the reference font size
   * @return the scaled icon
   */
  @NotNull
  public static Icon scaleByFont(@NotNull Icon icon, @Nullable Component ancestor, float fontSize) {
    float scale = JBUI.getFontScale(fontSize);
    if (icon instanceof ScalableIcon) {
      if (icon instanceof ScaleContextAware) {
        ScaleContextAware ctxIcon = (ScaleContextAware)icon;
        ctxIcon.updateScaleContext(ancestor != null ? ScaleContext.create(ancestor) : null);
        // take into account the user scale of the icon
        double usrScale = ctxIcon.getScaleContext().getScale(USR_SCALE);
        scale /= usrScale;
      }
      return ((ScalableIcon)icon).scale(scale);
    }
    return scale(icon, scale);
  }

  @NotNull
  public static Icon colorize(@NotNull Icon source, @NotNull Color color) {
    return colorize(source, color, false);
  }

  @NotNull
  public static Icon colorize(Graphics2D g, @NotNull Icon source, @NotNull Color color) {
    return colorize(g, source, color, false);
  }

  @NotNull
  public static Icon colorize(@NotNull Icon source, @NotNull Color color, boolean keepGray) {
    return filterIcon(null, source, new ColorFilter(color, keepGray));
  }

  @NotNull
  public static Icon colorize(Graphics2D g, @NotNull Icon source, @NotNull Color color, boolean keepGray) {
    return filterIcon(g, source, new ColorFilter(color, keepGray));
  }
  @NotNull
  public static Icon desaturate(@NotNull Icon source) {
    return filterIcon(null, source, new DesaturationFilter());
  }

  @NotNull
  public static Icon brighter(@NotNull Icon source, int tones) {
    return filterIcon(null, source, new BrighterFilter(tones));
  }

  @NotNull
  public static Icon darker(@NotNull Icon source, int tones) {
    return filterIcon(null, source, new DarkerFilter(tones));
  }

  @NotNull
  private static Icon filterIcon(Graphics2D g, @NotNull Icon source, @NotNull Filter filter) {
    BufferedImage src = g != null ? UIUtil.createImage(g, source.getIconWidth(), source.getIconHeight(), Transparency.TRANSLUCENT) :
                                    UIUtil.createImage(source.getIconWidth(), source.getIconHeight(), Transparency.TRANSLUCENT);
    Graphics2D g2d = src.createGraphics();
    source.paintIcon(null, g2d, 0, 0);
    g2d.dispose();
    BufferedImage img = g != null ? UIUtil.createImage(g, source.getIconWidth(), source.getIconHeight(), Transparency.TRANSLUCENT) :
                                    UIUtil.createImage(source.getIconWidth(), source.getIconHeight(), Transparency.TRANSLUCENT);
    int[] rgba = new int[4];
    for (int y = 0; y < src.getRaster().getHeight(); y++) {
      for (int x = 0; x < src.getRaster().getWidth(); x++) {
        src.getRaster().getPixel(x, y, rgba);
        if (rgba[3] != 0) {
          img.getRaster().setPixel(x, y, filter.convert(rgba));
        }
      }
    }
    return createImageIcon((Image)img);
  }

  private static abstract class Filter {
    @NotNull
    abstract int[] convert(@NotNull int[] rgba);
  }

  private static class ColorFilter extends Filter {
    private final float[] myBase;
    private final boolean myKeepGray;

    private ColorFilter(@NotNull Color color, boolean keepGray) {
      myKeepGray = keepGray;
      myBase = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    }

    @NotNull
    @Override
    int[] convert(@NotNull int[] rgba) {
      float[] hsb = new float[3];
      Color.RGBtoHSB(rgba[0], rgba[1], rgba[2], hsb);
      int rgb = Color.HSBtoRGB(myBase[0], myBase[1] * (myKeepGray ? hsb[1] : 1f), myBase[2] * hsb[2]);
      return new int[]{rgb >> 16 & 0xff, rgb >> 8 & 0xff, rgb & 0xff, rgba[3]};
    }
  }

  private static class DesaturationFilter extends Filter {
    @NotNull
    @Override
    int[] convert(@NotNull int[] rgba) {
      int min = Math.min(Math.min(rgba[0], rgba[1]), rgba[2]);
      int max = Math.max(Math.max(rgba[0], rgba[1]), rgba[2]);
      int grey = (max + min) / 2;
      return new int[]{grey, grey, grey, rgba[3]};
    }
  }

  private static class BrighterFilter extends Filter {
    private final int myTones;

    public BrighterFilter(int tones) {
      myTones = tones;
    }

    @NotNull
    @Override
    int[] convert(@NotNull int[] rgba) {
      final float[] hsb = Color.RGBtoHSB(rgba[0], rgba[1], rgba[2], null);
      float brightness = hsb[2];
      for (int i = 0; i < myTones; i++) {
        brightness = Math.min(1, brightness * 1.1F);
        if (brightness == 1) break;
      }
      Color color = Color.getHSBColor(hsb[0], hsb[1], brightness);
      return new int[]{color.getRed(), color.getGreen(), color.getBlue(), rgba[3]};
    }
  }

  private static class DarkerFilter extends Filter {
    private final int myTones;

    public DarkerFilter(int tones) {
      myTones = tones;
    }

    @NotNull
    @Override
    int[] convert(@NotNull int[] rgba) {
      final float[] hsb = Color.RGBtoHSB(rgba[0], rgba[1], rgba[2], null);
      float brightness = hsb[2];
      for (int i = 0; i < myTones; i++) {
        brightness = Math.max(0, brightness / 1.1F);
        if (brightness == 0) break;
      }
      Color color = Color.getHSBColor(hsb[0], hsb[1], brightness);
      return new int[]{color.getRed(), color.getGreen(), color.getBlue(), rgba[3]};
    }
  }

  /**
   * @deprecated Use {@link #createImageIcon(Image)}
   */
  @Deprecated
  @NotNull
  public static JBImageIcon createImageIcon(@NotNull final BufferedImage img) {
    return createImageIcon((Image)img);
  }

  @NotNull
  public static JBImageIcon createImageIcon(@NotNull final Image img) {
    return new JBImageIcon(img) {
      @Override
      public int getIconWidth() {
        return ImageUtil.getUserWidth(getImage());
      }

      @Override
      public int getIconHeight() {
        return ImageUtil.getUserHeight(getImage());
      }
    };
  }

  @NotNull
  public static Icon textToIcon(@NotNull final String text, @NotNull final Component component, final float fontSize) {
    final Font font = JBFont.create(JBUI.Fonts.label().deriveFont(fontSize));
    FontMetrics metrics = component.getFontMetrics(font);
    final int width = metrics.stringWidth(text) + JBUI.scale(4);
    final int height = metrics.getHeight();

    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        g = g.create();
        try {
          GraphicsUtil.setupAntialiasing(g);
          g.setFont(font);
          UIUtil.drawStringWithHighlighting(g, text, x + JBUI.scale(2), y + height - JBUI.scale(1), JBColor.foreground(), JBColor.background());
        }
        finally {
          g.dispose();
        }
      }

      @Override
      public int getIconWidth() {
        return width;
      }

      @Override
      public int getIconHeight() {
        return height;
      }
    };
  }

  @NotNull
  public static Icon addText(@NotNull Icon base, @NotNull String text) {
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(base, 0);
    icon.setIcon(textToIcon(text, new JLabel(), JBUI.scale(6f)), 1, SwingConstants.SOUTH_EAST);
    return icon;
  }

  /**
   * Creates new icon with the filter applied.
   */
  @Nullable
  public static Icon filterIcon(@NotNull Icon icon, RGBImageFilter filter, @Nullable Component ancestor) {
    return IconLoader.filterIcon(icon, filter, ancestor);
  }
}
