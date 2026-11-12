@file:OptIn(ExperimentalMaterial3Api::class)

/*
 * 与 Material3 同名、签名兼容的液态玻璃控件库。
 *
 * 只要把 androidx.compose.material3.Xxx 的 import 换成本包的 Xxx，
 * 现有调用点无需改动即可获得 Backdrop 风格的玻璃外观。
 *
 * 与上一版的关键区别：
 *  - 所有容器统一走 glassSurface()：真正采样 LocalGlassHazeState 的背景，
 *    而不是「半透明深色块 + 描边」；
 *  - 全屏/矩形 Surface 不再铺不透明底色，否则会盖住主界面底图；
 *  - 补齐 Checkbox / FilterChip / TextField 系 / TopAppBar / AlertDialog /
 *    ListItem / FilledTonalButton 等此前遗漏的控件。
 *
 * 尺寸与参数对齐 Kyant0/AndroidLiquidGlass 的示例控件：
 *   LiquidButton 48dp 胶囊 / LiquidToggle 64x28dp 轨道 + 40x24dp 拇指 /
 *   LiquidSlider 6dp 轨道 + 40x24dp 拇指 / LiquidBottomTabs 64dp 胶囊。
 */

package me.spica27.spicamusic.ui.glass

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar as MaterialCenterAlignedTopAppBar
import androidx.compose.material3.Checkbox as MaterialCheckbox
import androidx.compose.material3.CircularProgressIndicator as MaterialCircularProgressIndicator
import androidx.compose.material3.FilterChip as MaterialFilterChip
import androidx.compose.material3.ListItem as MaterialListItem
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.material3.Slider as MaterialSlider
import androidx.compose.material3.TextField as MaterialTextField
import androidx.compose.material3.TopAppBar as MaterialTopAppBar

/** 胶囊玻璃按钮：height 48dp，对应 LiquidButton。 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = GlassShapes.Capsule,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    GlassButtonLayout(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        spec = GlassSpec.Button,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** 文字按钮：更淡的玻璃，保持同样的胶囊轮廓。 */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = GlassShapes.Capsule,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    GlassButtonLayout(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        spec = GlassSpec.Card.copy(shadow = null),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** 描边按钮：玻璃 + 更明显的轮廓高光。 */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = GlassShapes.Capsule,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    GlassButtonLayout(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        spec = GlassSpec.Card.copy(highlight = HighlightSpec(width = 0.75.dp)),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** 填充色调按钮：与 Button 同形，稍强的着色。 */
@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = GlassShapes.Capsule,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    GlassButtonLayout(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        spec = GlassSpec.Button.copy(tint = GlassTint.control(MaterialTheme.colorScheme.surface.luminance() < 0.5f)),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
private fun GlassButtonLayout(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    spec: GlassSpec,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource?,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .height(48.dp)
                .glassSurface(spec = spec, shape = shape)
                .clip(shape)
                .clickable(
                    onClick = onClick,
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                ).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                content()
            }
        }
    }
}

/** 圆形玻璃图标按钮。 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = GlassShapes.Capsule,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(48.dp)
                .glassSurface(
                    spec =
                        GlassSpec.Card.copy(
                            shadow = ShadowSpec(radius = 8.dp, color = Color.Black.copy(alpha = 0.06f)),
                        ),
                    shape = shape,
                ).clip(shape)
                .clickable(
                    onClick = onClick,
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

/** 玻璃开关：64x28dp 轨道，40x24dp 可移动玻璃拇指（LiquidToggle 尺寸）。 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val accent =
        if (checked) GlassColors.ToggleOnDark else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val thumbOffset by
        animateDpAsState(
            targetValue = if (checked) 22.dp else 2.dp,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
            label = "glassSwitchThumb",
        )
    Box(
        modifier =
            modifier
                .size(width = 64.dp, height = 28.dp)
                .glassSurface(
                    spec = GlassSpec.Card.copy(shadow = null, highlight = HighlightSpec(width = 0.4.dp)),
                    shape = GlassShapes.Capsule,
                ).clip(GlassShapes.Capsule)
                .background(accent.copy(alpha = if (checked) 0.42f else 0.14f))
                .clickable(
                    enabled = enabled && onCheckedChange != null,
                    onClick = { onCheckedChange?.invoke(!checked) },
                    role = Role.Switch,
                    interactionSource = interactionSource,
                    indication = null,
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(start = thumbOffset)
                    .size(width = 40.dp, height = 24.dp)
                    .glassSurface(spec = GlassSpec.ToggleThumb, shape = GlassShapes.Capsule)
                    .clip(GlassShapes.Capsule)
                    .background(Color.White.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center,
        ) {
            thumbContent?.invoke()
        }
    }
}

/** 玻璃复选框：32dp 玻璃底座 + 原生勾选动画。 */
@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    Box(
        modifier =
            modifier
                .size(36.dp)
                .glassSurface(
                    spec = GlassSpec.Thumb.copy(shadow = null),
                    shape = GlassShapes.Small,
                ).clip(GlassShapes.Small),
        contentAlignment = Alignment.Center,
    ) {
        MaterialCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(22.dp),
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
        )
    }
}

/** 玻璃筛选标签：选中态用 LiquidBottomTabs 的 SelectedPill 规格。 */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = GlassShapes.Capsule,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = null,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    MaterialFilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier =
            modifier.glassSurface(
                spec = if (selected) GlassSpec.SelectedPill else GlassSpec.Card.copy(shadow = null),
                shape = shape,
            ),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent,
                selectedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
    )
}

/**
 * 玻璃滑块：6dp 轨道 + 40x24dp 玻璃拇指（LiquidSlider 尺寸）。
 * 保留原生 Slider 的拖拽手势，只替换轨道与拇指的绘制。
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
    thumb: (@Composable () -> Unit)? = null,
    track: (@Composable () -> Unit)? = null,
) {
    val fraction =
        ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val sliderInteraction = interactionSource ?: remember { MutableInteractionSource() }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .glassSurface(
                    spec = GlassSpec.Card.copy(shadow = null, highlight = HighlightSpec(width = 0.3.dp)),
                    shape = GlassShapes.Capsule,
                ),
        )
        MaterialSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = colors,
            interactionSource = sliderInteraction,
            thumb = {
                if (thumb != null) {
                    thumb()
                } else {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 24.dp)
                            .glassSurface(spec = GlassSpec.Thumb, shape = GlassShapes.Capsule)
                            .clip(GlassShapes.Capsule)
                            .background(Color.White.copy(alpha = 0.34f)),
                    )
                }
            },
            track = {
                if (track != null) {
                    track()
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(GlassShapes.Capsule)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(GlassShapes.Capsule)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            },
        )
    }
}

/** 细玻璃分隔线。 */
@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 0.5.dp,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        color.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.22f),
                        color.copy(alpha = 0.55f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

/** 玻璃质感的线性进度条。 */
@Composable
fun LinearProgressIndicator(
    progress: (() -> Float)? = null,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
) {
    val value = progress?.invoke()?.coerceIn(0f, 1f) ?: 0.35f
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(GlassShapes.Capsule)
            .glassOptics(
                spec = GlassSpec.Card.copy(shadow = null, highlight = HighlightSpec(width = 0.3.dp)),
                shape = GlassShapes.Capsule,
            ).background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxWidth(value)
                .height(6.dp)
                .clip(GlassShapes.Capsule)
                .background(color),
        )
    }
}

/** 玻璃环形进度：保留原生指示器，外层加一圈轮廓光。 */
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
) {
    Box(
        modifier.size(48.dp).glassSurface(
            spec = GlassSpec.Card.copy(shadow = null, highlight = HighlightSpec(width = 0.35.dp)),
            shape = RoundedCornerShape(50),
        ),
        contentAlignment = Alignment.Center,
    ) {
        MaterialCircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = color,
            strokeWidth = strokeWidth,
        )
    }
}

/** 玻璃容器：全屏矩形保持透明（不能挡住底图），圆角面板叠加光学层。 */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    GlassSurfaceLayout(modifier, shape, color, contentColor, content)
}

/** 可点击的玻璃容器，对应 M3 Surface(onClick) 重载。 */
@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    GlassSurfaceLayout(
        modifier =
            modifier.clickable(
                onClick = onClick,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            ),
        shape = shape,
        color = color,
        contentColor = contentColor,
        content = content,
    )
}

@Composable
private fun GlassSurfaceLayout(
    modifier: Modifier,
    shape: Shape,
    color: Color,
    contentColor: Color,
    content: @Composable () -> Unit,
) {
    val isPanel = shape != RectangleShape
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        if (!isPanel) {
            // 全屏容器必须透明，否则一层 surface 就把底图与所有玻璃折射挡住了。
            Box(modifier = modifier) { content() }
            return@CompositionLocalProvider
        }
        val scheme = MaterialTheme.colorScheme
        val isDark = scheme.surface.luminance() < 0.5f
        val tint =
            if (color == scheme.surface || color == scheme.surfaceContainerHigh) {
                GlassTint.panel(isDark)
            } else {
                color.copy(alpha = 0.30f)
            }
        Box(
            modifier =
                modifier
                    .glassSurface(
                        spec =
                            GlassSpec.Card.copy(
                                tint = tint,
                                shadow = ShadowSpec(radius = 12.dp, color = Color.Black.copy(alpha = 0.08f)),
                            ),
                        shape = shape,
                    ).clip(shape),
        ) {
            content()
        }
    }
}

/** 玻璃外框的输入框（描边款）：底色由 glassSurface 提供，文字保持原生焦点/键盘行为。 */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = GlassShapes.Small,
    colors: TextFieldColors =
        androidx.compose.material3.OutlinedTextFieldDefaults
            .colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    Box(
        modifier.glassSurface(
            spec = GlassSpec.Card.copy(shadow = ShadowSpec(radius = 8.dp, color = Color.Black.copy(alpha = 0.06f))),
            shape = shape,
        ),
    ) {
        MaterialOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            shape = shape,
            colors = colors,
            interactionSource = interactionSource,
        )
    }
}

/** 玻璃外框的输入框（填充款）：强制容器与下划线透明，避免原生灰底盖住玻璃。 */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = GlassShapes.Small,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    Box(
        modifier.glassSurface(
            spec = GlassSpec.Card.copy(shadow = ShadowSpec(radius = 8.dp, color = Color.Black.copy(alpha = 0.06f))),
            shape = shape,
        ),
    ) {
        MaterialTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            shape = shape,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            interactionSource = interactionSource,
        )
    }
}

/** 玻璃对话框：M3 原生对话框行为 + 透明容器 + Backdrop 光学层。 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = GlassShapes.ExtraLarge,
    containerColor: Color = Color.Transparent,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    MaterialAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier =
            modifier.glassSurface(
                spec = GlassSpec.Dialog,
                shape = shape,
            ),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

/** 玻璃列表项：只在崩溃页使用，保留 M3 排版，容器换成玻璃。 */
@Composable
fun ListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
) {
    MaterialListItem(
        headlineContent = headlineContent,
        modifier =
            modifier.glassSurface(
                spec = GlassSpec.Card.copy(shadow = null),
                shape = GlassShapes.Small,
            ),
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    )
}

private val GlassAppBarShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

/** 玻璃顶栏：容器透明，底部大圆角玻璃条。 */
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .glassSurface(spec = GlassSpec.Bar, shape = GlassAppBarShape),
    ) {
        MaterialTopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            expandedHeight = expandedHeight,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            scrollBehavior = scrollBehavior,
        )
    }
}

/** 玻璃居中标题顶栏。 */
@Composable
fun CenterAlignedTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .glassSurface(spec = GlassSpec.Bar, shape = GlassAppBarShape),
    ) {
        MaterialCenterAlignedTopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            expandedHeight = expandedHeight,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            scrollBehavior = scrollBehavior,
        )
    }
}
