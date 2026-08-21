package com.winlator.inputcontrols;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;

import androidx.core.graphics.ColorUtils;

import com.winlator.core.CubicBezierInterpolator;
import com.winlator.math.Mathf;
import com.winlator.widget.InputControlsView;
import com.winlator.widget.TouchpadView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

public class ControlElement {
    public static final float STICK_DEAD_ZONE = 0.15f;
    public static final float DPAD_DEAD_ZONE = 0.3f;
    public static final float STICK_SENSITIVITY = 3.0f;
    public static final float TRACKPAD_MIN_SPEED = 0.8f;
    public static final float TRACKPAD_MAX_SPEED = 20.0f;
    public static final byte TRACKPAD_ACCELERATION_THRESHOLD = 4;
    public static final short BUTTON_MIN_TIME_TO_KEEP_PRESSED = 300;
    public static final int LAYER_ALWAYS = -1;
    public static final int LAYER_COUNT = 3;

    public enum Type {
        BUTTON, D_PAD, RANGE_BUTTON, STICK, TRACKPAD, MOUSE_AREA;

        public static String[] names() {
            Type[] types = values();
            String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) names[i] = types[i].name().replace("_", "-");
            return names;
        }
    }
    public enum StickMode { FIXED, FLOATING, FOLLOW_THUMB }
    public enum MouseMode { RELATIVE_MOUSE, CAMERA_LOOK, DIRECT_TOUCH }
    public enum ExpandAction { NONE, TOGGLE_GROUP_1, TOGGLE_GROUP_2, TOGGLE_GROUP_3, TOGGLE_GROUP_4 }
    public enum LayerAction {
        NONE, NEXT, PREVIOUS, LAYER_1, LAYER_2, LAYER_3;

        public static String[] names() {
            return new String[]{"None", "Next Layer", "Previous Layer", "Layer 1", "Layer 2", "Layer 3"};
        }
    }

    public enum Shape {
        CIRCLE, RECT, ROUND_RECT, SQUARE;

        public static String[] names() {
            Shape[] shapes = values();
            String[] names = new String[shapes.length];
            for (int i = 0; i < shapes.length; i++) names[i] = shapes[i].name().replace("_", " ");
            return names;
        }
    }
    public enum Range {
        FROM_A_TO_Z(26), FROM_0_TO_9(10), FROM_F1_TO_F12(12), FROM_NP0_TO_NP9(10);
        public final byte max;

        Range(int max) {
            this.max = (byte)max;
        }

        public static String[] names() {
            Range[] ranges = values();
            String[] names = new String[ranges.length];
            for (int i = 0; i < ranges.length; i++) names[i] = ranges[i].name().replace("_", " ");
            return names;
        }
    }
    private final InputControlsView inputControlsView;
    private Type type = Type.BUTTON;
    private Shape shape = Shape.CIRCLE;
    private Binding[] bindings = {Binding.NONE, Binding.NONE, Binding.NONE, Binding.NONE};
    private float scale = 1.0f;
    private short x;
    private short y;
    private boolean selected = false;
    private boolean toggleSwitch = false;
    private int layer = 0;
    private LayerAction layerAction = LayerAction.NONE;
    private StickMode stickMode = StickMode.FIXED;
    private MouseMode mouseMode = MouseMode.RELATIVE_MOUSE;
    private ExpandAction expandAction = ExpandAction.NONE;
    private int expandGroup = 0;
    private float stickDeadZone = STICK_DEAD_ZONE;
    private float hitboxScale = 1.0f;
    private float mouseSensitivity = 1.0f;
    private float mouseAreaWidth = 1.0f;
    private float mouseAreaHeight = 1.0f;
    private boolean editorLocked = false;
    private float portraitX = 0.5f;
    private float portraitY = 0.5f;
    private float portraitScale = 1.0f;
    private float landscapeX = 0.5f;
    private float landscapeY = 0.5f;
    private float landscapeScale = 1.0f;
    private int currentPointerId = -1;
    private final Rect boundingBox = new Rect();
    private boolean[] states = new boolean[4];
    private boolean boundingBoxNeedsUpdate = true;
    private String text = "";
    private byte iconId;
    private Range range;
    private byte orientation;
    private PointF currentPosition;
    private PointF stickCenter;
    private RangeScroller scroller;
    private CubicBezierInterpolator interpolator;
    private Object touchTime;

    public ControlElement(InputControlsView inputControlsView) {
        this.inputControlsView = inputControlsView;
    }

    private void reset() {
        setBinding(Binding.NONE);
        scroller = null;

        if (type == Type.D_PAD || type == Type.STICK) {
            bindings[0] = Binding.KEY_W;
            bindings[1] = Binding.KEY_D;
            bindings[2] = Binding.KEY_S;
            bindings[3] = Binding.KEY_A;
        }
        else if (type == Type.TRACKPAD) {
            bindings[0] = Binding.MOUSE_MOVE_UP;
            bindings[1] = Binding.MOUSE_MOVE_RIGHT;
            bindings[2] = Binding.MOUSE_MOVE_DOWN;
            bindings[3] = Binding.MOUSE_MOVE_LEFT;
        }
        else if (type == Type.RANGE_BUTTON) {
            scroller = new RangeScroller(inputControlsView, this);
        }

        text = "";
        iconId = 0;
        range = null;
        boundingBoxNeedsUpdate = true;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
        reset();
    }

    public int getBindingCount() {
        return bindings.length;
    }

    public void setBindingCount(int bindingCount) {
        bindings = new Binding[bindingCount];
        setBinding(Binding.NONE);
        states = new boolean[bindingCount];
        boundingBoxNeedsUpdate = true;
    }

    public Shape getShape() {
        return shape;
    }

    public void setShape(Shape shape) {
        this.shape = shape;
        boundingBoxNeedsUpdate = true;
    }

    public Range getRange() {
        return range != null ? range : Range.FROM_A_TO_Z;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public byte getOrientation() {
        return orientation;
    }

    public void setOrientation(byte orientation) {
        this.orientation = orientation;
        boundingBoxNeedsUpdate = true;
    }

    public boolean isToggleSwitch() {
        return toggleSwitch;
    }

    public void setToggleSwitch(boolean toggleSwitch) {
        this.toggleSwitch = toggleSwitch;
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = Math.max(LAYER_ALWAYS, Math.min(LAYER_COUNT - 1, layer));
    }

    public LayerAction getLayerAction() {
        return layerAction;
    }

    public void setLayerAction(LayerAction layerAction) {
        this.layerAction = layerAction != null ? layerAction : LayerAction.NONE;
    }

    public StickMode getStickMode() { return stickMode; }
    public void setStickMode(StickMode value) { stickMode = value != null ? value : StickMode.FIXED; }
    public MouseMode getMouseMode() { return mouseMode; }
    public void setMouseMode(MouseMode value) { mouseMode = value != null ? value : MouseMode.RELATIVE_MOUSE; }
    public ExpandAction getExpandAction() { return expandAction; }
    public void setExpandAction(ExpandAction value) { expandAction = value != null ? value : ExpandAction.NONE; }
    public int getExpandGroup() { return expandGroup; }
    public void setExpandGroup(int value) { expandGroup = Math.max(0, Math.min(4, value)); }
    public float getStickDeadZone() { return stickDeadZone; }
    public void setStickDeadZone(float value) { stickDeadZone = Mathf.clamp(value, 0.0f, 0.9f); }
    public float getHitboxScale() { return hitboxScale; }
    public void setHitboxScale(float value) { hitboxScale = Mathf.clamp(value, 1.0f, 3.0f); }
    public float getMouseSensitivity() { return mouseSensitivity; }
    public void setMouseSensitivity(float value) { mouseSensitivity = Mathf.clamp(value, 0.1f, 5.0f); }
    public float getMouseAreaWidth() { return mouseAreaWidth; }
    public void setMouseAreaWidth(float value) { mouseAreaWidth = Mathf.clamp(value, 0.25f, 4.0f); boundingBoxNeedsUpdate = true; }
    public float getMouseAreaHeight() { return mouseAreaHeight; }
    public void setMouseAreaHeight(float value) { mouseAreaHeight = Mathf.clamp(value, 0.25f, 4.0f); boundingBoxNeedsUpdate = true; }
    public boolean isEditorLocked() { return editorLocked; }
    public void setEditorLocked(boolean value) { editorLocked = value; }

    public void setLayouts(float portraitX, float portraitY, float portraitScale,
                           float landscapeX, float landscapeY, float landscapeScale,
                           int width, int height, boolean landscape) {
        this.portraitX = portraitX;
        this.portraitY = portraitY;
        this.portraitScale = portraitScale;
        this.landscapeX = landscapeX;
        this.landscapeY = landscapeY;
        this.landscapeScale = landscapeScale;
        applyLayout(width, height, landscape);
    }

    public void initializeLayoutsFromCurrentPosition() {
        int width = inputControlsView.getWidth();
        int height = inputControlsView.getHeight();
        if (width <= 0 || height <= 0) return;

        float normalizedX = (float)x / width;
        float normalizedY = (float)y / height;
        portraitX = normalizedX;
        portraitY = normalizedY;
        portraitScale = scale;
        landscapeX = normalizedX;
        landscapeY = normalizedY;
        landscapeScale = scale;
    }

    public void captureLayout(int width, int height, boolean landscape) {
        if (width <= 0 || height <= 0) return;

        if (landscape) {
            landscapeX = (float)x / width;
            landscapeY = (float)y / height;
            landscapeScale = scale;
        }
        else {
            portraitX = (float)x / width;
            portraitY = (float)y / height;
            portraitScale = scale;
        }
    }

    public void captureCurrentLayout() {
        captureLayout(inputControlsView.getWidth(), inputControlsView.getHeight(), inputControlsView.isLandscape());
    }

    public void applyLayout(int width, int height, boolean landscape) {
        if (width <= 0 || height <= 0) return;

        if (landscape) {
            x = (short)Math.round(landscapeX * width);
            y = (short)Math.round(landscapeY * height);
            scale = landscapeScale;
        }
        else {
            x = (short)Math.round(portraitX * width);
            y = (short)Math.round(portraitY * height);
            scale = portraitScale;
        }

        boundingBoxNeedsUpdate = true;
    }

    public Binding getBindingAt(int index) {
        return index < bindings.length ? bindings[index] : Binding.NONE;
    }

    public void setBindingAt(int index, Binding binding) {
        if (index >= bindings.length) {
            int oldLength = bindings.length;
            bindings = Arrays.copyOf(bindings, index+1);
            Arrays.fill(bindings, oldLength-1, bindings.length, Binding.NONE);
            states = new boolean[bindings.length];
            boundingBoxNeedsUpdate = true;
        }
        bindings[index] = binding;
    }

    public void setBinding(Binding binding) {
        Arrays.fill(bindings, binding);
    }

    public void loadOptionalPropertiesFrom(ControlElement source) {
        type = source.type;
        shape = source.shape;
        bindings = Arrays.copyOf(source.bindings, source.bindings.length);
        states = new boolean[bindings.length];
        scale = source.scale;
        toggleSwitch = source.toggleSwitch;
        layer = source.layer;
        layerAction = source.layerAction;
        stickMode = source.stickMode;
        mouseMode = source.mouseMode;
        expandAction = source.expandAction;
        expandGroup = source.expandGroup;
        stickDeadZone = source.stickDeadZone;
        hitboxScale = source.hitboxScale;
        mouseSensitivity = source.mouseSensitivity;
        mouseAreaWidth = source.mouseAreaWidth;
        mouseAreaHeight = source.mouseAreaHeight;
        editorLocked = source.editorLocked;
        portraitX = source.portraitX;
        portraitY = source.portraitY;
        portraitScale = source.portraitScale;
        landscapeX = source.landscapeX;
        landscapeY = source.landscapeY;
        landscapeScale = source.landscapeScale;
        text = source.text;
        iconId = source.iconId;
        range = source.range;
        orientation = source.orientation;
        if (type == Type.RANGE_BUTTON) scroller = new RangeScroller(inputControlsView, this);
        boundingBoxNeedsUpdate = true;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
        boundingBoxNeedsUpdate = true;
    }

    public short getX() {
        return x;
    }

    public void setX(int x) {
        this.x = (short)x;
        boundingBoxNeedsUpdate = true;
    }

    public short getY() {
        return y;
    }

    public void setY(int y) {
        this.y = (short)y;
        boundingBoxNeedsUpdate = true;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public byte getIconId() {
        return iconId;
    }

    public void setIconId(int iconId) {
        this.iconId = (byte)iconId;
    }

    public Rect getBoundingBox() {
        if (boundingBoxNeedsUpdate) computeBoundingBox();
        return boundingBox;
    }

    private Rect computeBoundingBox() {
        int snappingSize = inputControlsView.getSnappingSize();
        int halfWidth = 0;
        int halfHeight = 0;

        switch (type) {
            case BUTTON:
                switch (shape) {
                    case RECT:
                    case ROUND_RECT:
                        halfWidth = snappingSize * 4;
                        halfHeight = snappingSize * 2;
                        break;
                    case SQUARE:
                        halfWidth = (int)(snappingSize * 2.5f);
                        halfHeight = (int)(snappingSize * 2.5f);
                        break;
                    case CIRCLE:
                        halfWidth = snappingSize * 3;
                        halfHeight = snappingSize * 3;
                        break;
                }
                break;
            case D_PAD: {
                halfWidth = snappingSize * 7;
                halfHeight = snappingSize * 7;
                break;
            }
            case TRACKPAD:
            case STICK: {
                halfWidth = snappingSize * 6;
                halfHeight = snappingSize * 6;
                break;
            }
            case MOUSE_AREA: {
                halfWidth = (int)(snappingSize * 10 * mouseAreaWidth);
                halfHeight = (int)(snappingSize * 7 * mouseAreaHeight);
                break;
            }
            case RANGE_BUTTON: {
                halfWidth = snappingSize * ((bindings.length * 4) / 2);
                halfHeight = snappingSize * 2;

                if (orientation == 1) {
                    int tmp = halfWidth;
                    halfWidth = halfHeight;
                    halfHeight = tmp;
                }
                break;
            }
        }

        halfWidth *= scale;
        halfHeight *= scale;
        boundingBox.set(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight);
        boundingBoxNeedsUpdate = false;
        return boundingBox;
    }

    private String getDisplayText() {
        if (text != null && !text.isEmpty()) {
            return text;
        }
        else if (layerAction != LayerAction.NONE) {
            switch (layerAction) {
                case NEXT:
                    return "NEXT";
                case PREVIOUS:
                    return "PREV";
                case LAYER_1:
                    return "L1";
                case LAYER_2:
                    return "L2";
                case LAYER_3:
                    return "L3";
                default:
                    return "";
            }
        }
        else {
            Binding binding = getBindingAt(0);
            String text = binding.toString().replace("NUMPAD ", "NP").replace("BUTTON ", "");
            if (text.length() > 7) {
                String[] parts = text.split(" ");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) sb.append(part.charAt(0));
                return (binding.isMouse() ? "M" : "")+ sb;
            }
            else return text;
        }
    }

    private static float getTextSizeForWidth(Paint paint, String text, float desiredWidth) {
        final byte testTextSize = 48;
        paint.setTextSize(testTextSize);
        return testTextSize * desiredWidth / paint.measureText(text);
    }

    private static String getRangeTextForIndex(Range range, int index) {
        String text = "";
        switch (range) {
            case FROM_A_TO_Z:
                text = String.valueOf((char)(65 + index));
                break;
            case FROM_0_TO_9:
                text = String.valueOf((index + 1) % 10);
                break;
            case FROM_F1_TO_F12:
                text = "F"+(index + 1);
                break;
            case FROM_NP0_TO_NP9:
                text = "NP"+((index + 1) % 10);
                break;
        }
        return text;
    }

    public void draw(Canvas canvas) {
        int snappingSize = inputControlsView.getSnappingSize();
        Paint paint = inputControlsView.getPaint();
        int primaryColor = inputControlsView.getPrimaryColor();

        paint.setColor(selected ? inputControlsView.getSecondaryColor() : primaryColor);
        paint.setStyle(Paint.Style.STROKE);
        float strokeWidth = snappingSize * 0.25f;
        paint.setStrokeWidth(strokeWidth);
        Rect boundingBox = getBoundingBox();

        switch (type) {
            case BUTTON: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();

                switch (shape) {
                    case CIRCLE:
                        canvas.drawCircle(cx, cy, boundingBox.width() * 0.5f, paint);
                        break;
                    case RECT:
                        canvas.drawRect(boundingBox, paint);
                        break;
                    case ROUND_RECT: {
                        float radius = boundingBox.height() * 0.5f;
                        canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                        break;
                    }
                    case SQUARE: {
                        float radius = snappingSize * 0.75f * scale;
                        canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                        break;
                    }
                }

                if (iconId > 0) {
                    drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId);
                }
                else {
                    String text = getDisplayText();
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2), snappingSize * 2 * scale));
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(primaryColor);
                    canvas.drawText(text, x, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                }
                break;
            }
            case D_PAD: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                float offsetX = snappingSize * 2 * scale;
                float offsetY = snappingSize * 3 * scale;
                float start = snappingSize * scale;
                Path path = inputControlsView.getPath();
                path.reset();

                path.moveTo(cx, cy - start);
                path.lineTo(cx - offsetX, cy - offsetY);
                path.lineTo(cx - offsetX, boundingBox.top);
                path.lineTo(cx + offsetX, boundingBox.top);
                path.lineTo(cx + offsetX, cy - offsetY);
                path.close();

                path.moveTo(cx - start, cy);
                path.lineTo(cx - offsetY, cy - offsetX);
                path.lineTo(boundingBox.left, cy - offsetX);
                path.lineTo(boundingBox.left, cy + offsetX);
                path.lineTo(cx - offsetY, cy + offsetX);
                path.close();

                path.moveTo(cx, cy + start);
                path.lineTo(cx - offsetX, cy + offsetY);
                path.lineTo(cx - offsetX, boundingBox.bottom);
                path.lineTo(cx + offsetX, boundingBox.bottom);
                path.lineTo(cx + offsetX, cy + offsetY);
                path.close();

                path.moveTo(cx + start, cy);
                path.lineTo(cx + offsetY, cy - offsetX);
                path.lineTo(boundingBox.right, cy - offsetX);
                path.lineTo(boundingBox.right, cy + offsetX);
                path.lineTo(cx + offsetY, cy + offsetX);
                path.close();

                canvas.drawPath(path, paint);
                break;
            }
            case RANGE_BUTTON: {
                Range range = getRange();
                int oldColor = paint.getColor();
                float radius = snappingSize * 0.75f * scale;
                float elementSize = scroller.getElementSize();
                float minTextSize = snappingSize * 2 * scale;
                float scrollOffset = scroller.getScrollOffset();
                byte[] rangeIndex = scroller.getRangeIndex();
                Path path = inputControlsView.getPath();
                path.reset();

                if (orientation == 0) {
                    float lineTop = boundingBox.top + strokeWidth * 0.5f;
                    float lineBottom = boundingBox.bottom - strokeWidth * 0.5f;
                    float startX = boundingBox.left;
                    canvas.drawRoundRect(startX, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);

                    canvas.save();
                    path.addRoundRect(startX, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, Path.Direction.CW);
                    canvas.clipPath(path);
                    startX -= scrollOffset % elementSize;

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(oldColor);

                        if (startX > boundingBox.left && startX  < boundingBox.right) canvas.drawLine(startX, lineTop, startX, lineBottom, paint);
                        String text = getRangeTextForIndex(range, index);

                        if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(primaryColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, elementSize - strokeWidth * 2), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(text, startX + elementSize * 0.5f, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                        }
                        startX += elementSize;
                    }

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    canvas.restore();
                }
                else {
                    float lineLeft = boundingBox.left + strokeWidth * 0.5f;
                    float lineRight = boundingBox.right - strokeWidth * 0.5f;
                    float startY = boundingBox.top;
                    canvas.drawRoundRect(boundingBox.left, startY, boundingBox.right, boundingBox.bottom, radius, radius, paint);

                    canvas.save();
                    path.addRoundRect(boundingBox.left, startY, boundingBox.right, boundingBox.bottom, radius, radius, Path.Direction.CW);
                    canvas.clipPath(inputControlsView.getPath());
                    startY -= scrollOffset % elementSize;

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(oldColor);

                        if (startY > boundingBox.top && startY < boundingBox.bottom) canvas.drawLine(lineLeft, startY, lineRight, startY, paint);
                        String text = getRangeTextForIndex(range, i);

                        if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(primaryColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(text, x, startY + elementSize * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                        }
                        startY += elementSize;
                    }

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    canvas.restore();
                }
                break;
            }
            case STICK: {
                float cx = stickCenter != null ? stickCenter.x : boundingBox.centerX();
                float cy = stickCenter != null ? stickCenter.y : boundingBox.centerY();
                int oldColor = paint.getColor();
                canvas.drawCircle(cx, cy, boundingBox.height() * 0.5f, paint);

                float thumbstickX = currentPosition != null ? currentPosition.x : cx;
                float thumbstickY = currentPosition != null ? currentPosition.y : cy;

                short thumbRadius = (short) (snappingSize * 3.5f * scale);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ColorUtils.setAlphaComponent(primaryColor, 50));
                canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(oldColor);
                canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius + strokeWidth * 0.5f, paint);
                break;
            }
            case TRACKPAD: {
                float radius = boundingBox.height() * 0.15f;
                canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                float offset = strokeWidth * 2.5f;
                float innerStrokeWidth = strokeWidth * 2;
                float innerHeight = boundingBox.height() - offset * 2;
                radius = (innerHeight / boundingBox.height()) * radius - (innerStrokeWidth * 0.5f + strokeWidth * 0.5f);
                paint.setStrokeWidth(innerStrokeWidth);
                canvas.drawRoundRect(boundingBox.left + offset, boundingBox.top + offset, boundingBox.right - offset, boundingBox.bottom - offset, radius, radius, paint);
                break;
            }
            case MOUSE_AREA: {
                if (inputControlsView.isEditMode()) {
                    paint.setStyle(Paint.Style.STROKE);
                    canvas.drawRect(boundingBox, paint);
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTextSize(snappingSize * 1.5f * scale);
                    canvas.drawText("MOUSE AREA", boundingBox.centerX(), boundingBox.centerY(), paint);
                }
                break;
            }
        }
    }

    private void drawIcon(Canvas canvas, float cx, float cy, float width, float height, int iconId) {
        Paint paint = inputControlsView.getPaint();
        Bitmap icon = inputControlsView.getIcon((byte)iconId);
        paint.setColorFilter(inputControlsView.getColorFilter());
        int margin = (int)(inputControlsView.getSnappingSize() * (shape == Shape.CIRCLE || shape == Shape.SQUARE ? 2.0f : 1.0f) * scale);
        int halfSize = (int)((Math.min(width, height) - margin) * 0.5f);

        Rect srcRect = new Rect(0, 0, icon.getWidth(), icon.getHeight());
        Rect dstRect = new Rect((int)(cx - halfSize), (int)(cy - halfSize), (int)(cx + halfSize), (int)(cy + halfSize));
        canvas.drawBitmap(icon, srcRect, dstRect, paint);
        paint.setColorFilter(null);
    }

    public JSONObject toJSONObject() {
        try {
            captureCurrentLayout();
            JSONObject elementJSONObject = new JSONObject();
            elementJSONObject.put("type", type.name());
            elementJSONObject.put("shape", shape.name());

            JSONArray bindingsJSONArray = new JSONArray();
            for (Binding binding : bindings) bindingsJSONArray.put(binding.name());

            elementJSONObject.put("bindings", bindingsJSONArray);

            // Keep legacy fields as the portrait layout for compatibility with older builds.
            elementJSONObject.put("scale", Float.valueOf(portraitScale));
            elementJSONObject.put("x", portraitX);
            elementJSONObject.put("y", portraitY);

            elementJSONObject.put("portraitX", portraitX);
            elementJSONObject.put("portraitY", portraitY);
            elementJSONObject.put("portraitScale", Float.valueOf(portraitScale));
            elementJSONObject.put("landscapeX", landscapeX);
            elementJSONObject.put("landscapeY", landscapeY);
            elementJSONObject.put("landscapeScale", Float.valueOf(landscapeScale));
            elementJSONObject.put("layer", layer);
            elementJSONObject.put("layerAction", layerAction.name());
            elementJSONObject.put("stickMode", stickMode.name());
            elementJSONObject.put("stickDeadZone", stickDeadZone);
            elementJSONObject.put("hitboxScale", hitboxScale);
            elementJSONObject.put("mouseMode", mouseMode.name());
            elementJSONObject.put("mouseSensitivity", mouseSensitivity);
            elementJSONObject.put("mouseAreaWidth", mouseAreaWidth);
            elementJSONObject.put("mouseAreaHeight", mouseAreaHeight);
            elementJSONObject.put("expandGroup", expandGroup);
            elementJSONObject.put("expandAction", expandAction.name());
            elementJSONObject.put("editorLocked", editorLocked);
            elementJSONObject.put("toggleSwitch", toggleSwitch);
            elementJSONObject.put("text", text);
            elementJSONObject.put("iconId", iconId);

            if (type == Type.RANGE_BUTTON && range != null) {
                elementJSONObject.put("range", range.name());
                if (orientation != 0) elementJSONObject.put("orientation", orientation);
            }
            return elementJSONObject;
        }
        catch (JSONException e) {
            return null;
        }
    }

    public boolean containsPoint(float x, float y) {
        Rect box = getBoundingBox();
        float halfWidth = box.width() * hitboxScale * 0.5f;
        float halfHeight = box.height() * hitboxScale * 0.5f;
        return x >= box.centerX() - halfWidth && x <= box.centerX() + halfWidth &&
               y >= box.centerY() - halfHeight && y <= box.centerY() + halfHeight;
    }

    public void drawHitbox(Canvas canvas) {
        Rect box = getBoundingBox();
        float halfWidth = box.width() * hitboxScale * 0.5f;
        float halfHeight = box.height() * hitboxScale * 0.5f;
        Paint paint = inputControlsView.getPaint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, inputControlsView.getSnappingSize() * 0.15f));
        paint.setColor(0xffff9800);
        canvas.drawRect(box.centerX() - halfWidth, box.centerY() - halfHeight,
            box.centerX() + halfWidth, box.centerY() + halfHeight, paint);
    }

    private boolean isKeepButtonPressedAfterMinTime() {
        Binding binding = getBindingAt(0);
        return !toggleSwitch && (binding == Binding.GAMEPAD_BUTTON_L3 || binding == Binding.GAMEPAD_BUTTON_R3);
    }

    public boolean handleTouchDown(int pointerId, float x, float y) {
        if (currentPointerId == -1 && containsPoint(x, y)) {
            currentPointerId = pointerId;
            inputControlsView.performControlHaptic();

            if (type == Type.BUTTON) {
                if (layerAction != LayerAction.NONE) return true;
                if (expandAction != ExpandAction.NONE) return true;
                if (isKeepButtonPressedAfterMinTime()) touchTime = System.currentTimeMillis();
                if (!toggleSwitch || !selected) pressButtonBindings();
                return true;
            }
            else if (type == Type.RANGE_BUTTON) {
                scroller.handleTouchDown(x, y);
                return true;
            }
            else {
                if (type == Type.TRACKPAD || type == Type.MOUSE_AREA) {
                    if (currentPosition == null) currentPosition = new PointF();
                    currentPosition.set(x, y);
                }
                if (type == Type.STICK && stickMode != StickMode.FIXED) {
                    if (stickCenter == null) stickCenter = new PointF();
                    stickCenter.set(x, y);
                }
                if (type == Type.MOUSE_AREA) {
                    if (mouseMode == MouseMode.CAMERA_LOOK) inputControlsView.handleInputEvent(Binding.MOUSE_RIGHT_BUTTON, true);
                    else if (mouseMode == MouseMode.DIRECT_TOUCH) inputControlsView.handleInputEvent(Binding.MOUSE_LEFT_BUTTON, true);
                }
                return handleTouchMove(pointerId, x, y);
            }
        }
        else return false;
    }

    public boolean handleTouchMove(int pointerId, float x, float y) {
        if (pointerId == currentPointerId && (type == Type.D_PAD || type == Type.STICK || type == Type.TRACKPAD || type == Type.MOUSE_AREA)) {
            float deltaX, deltaY;
            Rect boundingBox = getBoundingBox();
            float radius = boundingBox.width() * 0.5f;
            float centerX = boundingBox.centerX();
            float centerY = boundingBox.centerY();
            TouchpadView touchpadView =  inputControlsView.getTouchpadView();

            if (type == Type.MOUSE_AREA) {
                if (mouseMode == MouseMode.DIRECT_TOUCH) {
                    inputControlsView.getXServer().injectPointerMove((int)(x * inputControlsView.getXServer().screenInfo.width / inputControlsView.getWidth()),
                        (int)(y * inputControlsView.getXServer().screenInfo.height / inputControlsView.getHeight()));
                }
                else if (currentPosition != null) {
                    inputControlsView.getXServer().injectPointerMoveDelta(Math.round((x-currentPosition.x)*mouseSensitivity), Math.round((y-currentPosition.y)*mouseSensitivity));
                }
                if (currentPosition != null) currentPosition.set(x, y);
                return true;
            }
            else if (type == Type.TRACKPAD) {
                if (currentPosition == null) currentPosition = new PointF();
                float[] deltaPoint = touchpadView.computeDeltaPoint(currentPosition.x, currentPosition.y, x, y);
                deltaX = deltaPoint[0];
                deltaY = deltaPoint[1];
                currentPosition.set(x, y);
            }
            else {
                if (type == Type.STICK && stickMode != StickMode.FIXED && stickCenter != null) {
                    centerX = stickCenter.x;
                    centerY = stickCenter.y;
                    if (stickMode == StickMode.FOLLOW_THUMB) {
                        float dx = x-centerX, dy = y-centerY, length = (float)Math.sqrt(dx*dx+dy*dy);
                        if (length > radius) { centerX += dx*(length-radius)/length; centerY += dy*(length-radius)/length; stickCenter.set(centerX, centerY); }
                    }
                }
                float localX = x - (centerX-radius);
                float localY = y - (centerY-radius);
                float offsetX = localX - radius;
                float offsetY = localY - radius;

                float distance = Mathf.lengthSq(radius - localX, radius - localY);
                if (distance > radius * radius) {
                    float angle = (float)Math.atan2(offsetY, offsetX);
                    offsetX = (float)(Math.cos(angle) * radius);
                    offsetY = (float)(Math.sin(angle) * radius);
                }

                deltaX = Mathf.clamp(offsetX / radius, -1, 1);
                deltaY = Mathf.clamp(offsetY / radius, -1, 1);
            }

            if (type == Type.STICK) {
                if (currentPosition == null) currentPosition = new PointF();
                currentPosition.x = centerX + deltaX * radius;
                currentPosition.y = centerY + deltaY * radius;
                final boolean[] states = {deltaY <= -stickDeadZone, deltaX >= stickDeadZone, deltaY >= stickDeadZone, deltaX <= -stickDeadZone};

                for (byte i = 0; i < 4; i++) {
                    float value = i == 1 || i == 3 ? deltaX : deltaY;
                    Binding binding = getBindingAt(i);
                    if (binding.isGamepad()) {
                        value = Math.abs(value) < stickDeadZone ? 0 :
                            Mathf.clamp(Math.max(0, Math.abs(value) - 0.01f) * Mathf.sign(value) * STICK_SENSITIVITY, -1, 1);
                        inputControlsView.handleInputEvent(binding, true, value);
                        this.states[i] = true;
                    }
                    else {
                        boolean state = binding.isMouseMove() ? (states[i] || states[(i+2)%4]) : states[i];
                        inputControlsView.handleInputEvent(binding, state, value);
                        this.states[i] = state;
                    }
                }

                inputControlsView.invalidate();
            }
            else if (type == Type.TRACKPAD) {
                final boolean[] states = {deltaY <= -TRACKPAD_MIN_SPEED, deltaX >= TRACKPAD_MIN_SPEED, deltaY >= TRACKPAD_MIN_SPEED, deltaX <= -TRACKPAD_MIN_SPEED};
                int cursorDx = 0;
                int cursorDy = 0;

                for (byte i = 0; i < 4; i++) {
                    float value = (i == 1 || i == 3 ? deltaX : deltaY);
                    Binding binding = getBindingAt(i);
                    if (binding.isGamepad()) {
                        if (interpolator == null) interpolator = new CubicBezierInterpolator();
                        if (Math.abs(value) > TRACKPAD_ACCELERATION_THRESHOLD) value *= STICK_SENSITIVITY;
                        interpolator.set(0.075f, 0.95f, 0.45f, 0.95f);
                        float interpolatedValue = interpolator.getInterpolation(Math.min(1.0f, Math.abs(value / TRACKPAD_MAX_SPEED)));
                        inputControlsView.handleInputEvent(binding, true, Mathf.clamp(interpolatedValue * Mathf.sign(value), -1, 1));
                        this.states[i] = true;
                    }
                    else {
                        if (Math.abs(value) > TouchpadView.CURSOR_ACCELERATION_THRESHOLD) value *= TouchpadView.CURSOR_ACCELERATION;
                        if (binding == Binding.MOUSE_MOVE_LEFT || binding == Binding.MOUSE_MOVE_RIGHT) {
                            cursorDx = Mathf.roundPoint(value);
                        }
                        else if (binding == Binding.MOUSE_MOVE_UP || binding == Binding.MOUSE_MOVE_DOWN) {
                            cursorDy = Mathf.roundPoint(value);
                        }
                        else {
                            inputControlsView.handleInputEvent(binding, states[i], value);
                            this.states[i] = states[i];
                        }
                    }
                }

                if (cursorDx != 0 || cursorDy != 0) inputControlsView.getXServer().injectPointerMoveDelta(cursorDx, cursorDy);
            }
            else {
                final boolean[] states = {deltaY <= -DPAD_DEAD_ZONE, deltaX >= DPAD_DEAD_ZONE, deltaY >= DPAD_DEAD_ZONE, deltaX <= -DPAD_DEAD_ZONE};

                for (byte i = 0; i < 4; i++) {
                    float value = i == 1 || i == 3 ? deltaX : deltaY;
                    Binding binding = getBindingAt(i);
                    boolean state = binding.isMouseMove() ? (states[i] || states[(i+2)%4]) : states[i];
                    inputControlsView.handleInputEvent(binding, state, value);
                    this.states[i] = state;
                }
            }

            return true;
        }
        else if (pointerId == currentPointerId && type == Type.RANGE_BUTTON) {
            scroller.handleTouchMove(x, y);
            return true;
        }
        else return false;
    }

    public boolean handleTouchUp(int pointerId) {
        if (pointerId == currentPointerId) {
            if (type == Type.BUTTON) {
                if (layerAction != LayerAction.NONE) {
                    currentPointerId = -1;
                    inputControlsView.handleLayerAction(layerAction);
                    return true;
                }
                if (expandAction != ExpandAction.NONE) {
                    currentPointerId = -1;
                    inputControlsView.handleExpandAction(expandAction);
                    return true;
                }

                Binding binding = getBindingAt(0);
                if (isKeepButtonPressedAfterMinTime() && touchTime != null) {
                    selected = (System.currentTimeMillis() - (long)touchTime) > BUTTON_MIN_TIME_TO_KEEP_PRESSED;
                    if (!selected) releaseButtonBindings();
                    touchTime = null;
                    inputControlsView.invalidate();
                }
                else if (!toggleSwitch || selected) releaseButtonBindings();

                if (toggleSwitch) {
                    selected = !selected;
                    inputControlsView.invalidate();
                }
            }
            else if (type == Type.RANGE_BUTTON || type == Type.D_PAD || type == Type.STICK || type == Type.TRACKPAD || type == Type.MOUSE_AREA) {
                if (type == Type.MOUSE_AREA) {
                    if (mouseMode == MouseMode.CAMERA_LOOK) inputControlsView.handleInputEvent(Binding.MOUSE_RIGHT_BUTTON, false);
                    else if (mouseMode == MouseMode.DIRECT_TOUCH) inputControlsView.handleInputEvent(Binding.MOUSE_LEFT_BUTTON, false);
                }
                for (byte i = 0; i < states.length; i++) {
                    if (states[i]) inputControlsView.handleInputEvent(getBindingAt(i), false);
                    states[i] = false;
                }

                if (type == Type.RANGE_BUTTON) {
                    scroller.handleTouchUp();
                }
                else if (type == Type.STICK) {
                    inputControlsView.invalidate();
                }

                if (currentPosition != null) currentPosition = null;
                stickCenter = null;
            }
            currentPointerId = -1;
            return true;
        }
        return false;
    }

    public void cancelTouch() {
        if (currentPointerId != -1) {
            if (type == Type.BUTTON && (layerAction != LayerAction.NONE || expandAction != ExpandAction.NONE)) {
                currentPointerId = -1;
            }
            else {
                handleTouchUp(currentPointerId);
            }
        }

        if (type == Type.BUTTON && layerAction == LayerAction.NONE && expandAction == ExpandAction.NONE && toggleSwitch && selected) {
            releaseButtonBindings();
            selected = false;
            inputControlsView.invalidate();
        }
    }

    private void pressButtonBindings() {
        for (int i = 1; i < Math.min(4, bindings.length); i++) if (bindings[i] != Binding.NONE) inputControlsView.handleInputEvent(bindings[i], true);
        inputControlsView.handleInputEvent(getBindingAt(0), true);
    }

    private void releaseButtonBindings() {
        inputControlsView.handleInputEvent(getBindingAt(0), false);
        for (int i = Math.min(3, bindings.length-1); i >= 1; i--) if (bindings[i] != Binding.NONE) inputControlsView.handleInputEvent(bindings[i], false);
    }
}
