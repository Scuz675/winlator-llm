package com.winlator;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.inputcontrols.Binding;
import com.winlator.inputcontrols.ControlElement;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.InputControlsManager;
import com.winlator.math.Mathf;
import com.winlator.core.AppUtils;
import com.winlator.core.FileUtils;
import com.winlator.core.UnitUtils;
import com.winlator.widget.InputControlsView;
import com.winlator.widget.NumberPicker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ControlsEditorActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int REQUEST_EDITOR_BACKGROUND = 4101;
    private static final String EDITOR_BACKGROUND_DIR = "control_backgrounds";

    private InputControlsView inputControlsView;
    private ControlsProfile profile;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        AppUtils.hideSystemUI(this);
        setContentView(R.layout.controls_editor_activity);

        inputControlsView = new InputControlsView(this);
        inputControlsView.setEditMode(true);
        inputControlsView.setOverlayOpacity(0.6f);

        profile = InputControlsManager.loadProfile(this, ControlsProfile.getProfileFile(this, getIntent().getIntExtra("profile_id", 0)));
        ((TextView)findViewById(R.id.TVProfileName)).setText(profile.getName());
        inputControlsView.setProfile(profile);
        loadEditorBackground();

        FrameLayout container = findViewById(R.id.FLContainer);
        container.addView(inputControlsView, 0);

        container.findViewById(R.id.BTAddElement).setOnClickListener(this);
        container.findViewById(R.id.BTRemoveElement).setOnClickListener(this);
        container.findViewById(R.id.BTElementSettings).setOnClickListener(this);
        container.findViewById(R.id.BTSave).setOnClickListener(this);
        container.findViewById(R.id.BTBackground).setOnClickListener(this);
        container.findViewById(R.id.BTEditorLayer).setOnClickListener(this);
        updateEditorLayerButton();
        container.findViewById(R.id.BTBackground).setOnLongClickListener(v -> {
            removeEditorBackground();
            return true;
        });
        container.findViewById(R.id.BTAddElement).setOnLongClickListener(v -> {
            if (!inputControlsView.duplicateSelectedElement()) AppUtils.showToast(this, R.string.no_control_element_selected);
            return true;
        });
        container.findViewById(R.id.BTElementSettings).setOnLongClickListener(v -> {
            inputControlsView.setShowHitboxes(!inputControlsView.isShowHitboxes());
            return true;
        });
    }

    @Override
    protected void onPause() {
        if (profile != null) {
            profile.save();
        }

        super.onPause();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        inputControlsView.post(this::loadEditorBackground);
    }

@Override
public void onClick(View v) {
    switch (v.getId()) {
        case R.id.BTAddElement:
            if (!inputControlsView.addElement()) {
                AppUtils.showToast(this, R.string.no_profile_selected);
            }
            break;

        case R.id.BTRemoveElement:
            if (!inputControlsView.removeElement()) {
                AppUtils.showToast(this, R.string.no_control_element_selected);
            }
            break;

        case R.id.BTBackground:
            chooseEditorBackground();
            break;

        case R.id.BTEditorLayer:
            inputControlsView.setEditorLayer((inputControlsView.getEditorLayer() + 1) % ControlElement.LAYER_COUNT);
            updateEditorLayerButton();
            break;

        case R.id.BTElementSettings:
            ControlElement selectedElement = inputControlsView.getSelectedElement();

            if (selectedElement != null) {
                showControlElementSettings(v);
            }
            else {
                AppUtils.showToast(this, R.string.no_control_element_selected);
            }
            break;

        case R.id.BTSave:
            if (profile != null) {
                profile.save();
                AppUtils.showToast(this, R.string.controls_saved);
            }
            break;
    }
}

    private File getEditorBackgroundFile() {
        File directory = new File(getFilesDir(), EDITOR_BACKGROUND_DIR);
        if (!directory.exists()) directory.mkdirs();

        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        String suffix = landscape ? "-landscape" : "";
        return new File(directory, "controls-" + profile.id + suffix + ".img");
    }

    private void updateEditorLayerButton() {
        TextView button = findViewById(R.id.BTEditorLayer);
        if (button != null) button.setText("L" + (inputControlsView.getEditorLayer() + 1));
    }

    private void chooseEditorBackground() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_EDITOR_BACKGROUND);
    }

    private void loadEditorBackground() {
        if (profile == null) return;

        File file = getEditorBackgroundFile();
        if (!file.isFile()) {
            inputControlsView.clearEditorBackground();
            return;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap != null) {
            inputControlsView.setEditorBackground(bitmap);
        }
        else {
            inputControlsView.clearEditorBackground();
        }
    }

    private void saveEditorBackground(Uri uri) {
        if (profile == null || uri == null) return;

        File target = getEditorBackgroundFile();

        try (
            InputStream input = getContentResolver().openInputStream(uri);
            OutputStream output = new FileOutputStream(target)
        ) {
            if (input == null) throw new IOException("Could not open selected image");

            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }

            loadEditorBackground();
            AppUtils.showToast(this, R.string.controls_background_saved);
        }
        catch (IOException e) {
            if (target.exists()) target.delete();
            AppUtils.showToast(this, R.string.controls_background_error);
        }
    }

    private void removeEditorBackground() {
        if (profile == null) return;

        File file = getEditorBackgroundFile();
        if (file.exists()) file.delete();

        inputControlsView.clearEditorBackground();
        AppUtils.showToast(this, R.string.controls_background_removed);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EDITOR_BACKGROUND &&
            resultCode == Activity.RESULT_OK &&
            data != null &&
            data.getData() != null) {
            saveEditorBackground(data.getData());
        }
    }
    private void showControlElementSettings(View anchorView) {
        final ControlElement element = inputControlsView.getSelectedElement();
        View view = LayoutInflater.from(this).inflate(R.layout.control_element_settings, null);

        final Runnable updateLayout = () -> {
            ControlElement.Type type = element.getType();
            view.findViewById(R.id.LLShape).setVisibility(View.GONE);
            view.findViewById(R.id.CBToggleSwitch).setVisibility(View.GONE);
            view.findViewById(R.id.LLCustomTextIcon).setVisibility(View.GONE);
            view.findViewById(R.id.LLRangeOptions).setVisibility(View.GONE);
            view.findViewById(R.id.LLLayerAction).setVisibility(View.GONE);
            view.findViewById(R.id.LLExpandAction).setVisibility(View.GONE);
            view.findViewById(R.id.LLStickOptions).setVisibility(View.GONE);
            view.findViewById(R.id.LLMouseOptions).setVisibility(View.GONE);

            if (type == ControlElement.Type.BUTTON) {
                view.findViewById(R.id.LLShape).setVisibility(View.VISIBLE);
                view.findViewById(R.id.CBToggleSwitch).setVisibility(View.VISIBLE);
                view.findViewById(R.id.LLCustomTextIcon).setVisibility(View.VISIBLE);
                view.findViewById(R.id.LLLayerAction).setVisibility(View.VISIBLE);
                view.findViewById(R.id.LLExpandAction).setVisibility(View.VISIBLE);
            }
            else if (type == ControlElement.Type.RANGE_BUTTON) {
                view.findViewById(R.id.LLRangeOptions).setVisibility(View.VISIBLE);
            }
            else if (type == ControlElement.Type.STICK) view.findViewById(R.id.LLStickOptions).setVisibility(View.VISIBLE);
            else if (type == ControlElement.Type.MOUSE_AREA) view.findViewById(R.id.LLMouseOptions).setVisibility(View.VISIBLE);

            loadBindingSpinners(element, view);
        };

        loadTypeSpinner(element, view.findViewById(R.id.SType), updateLayout);
        loadShapeSpinner(element, view.findViewById(R.id.SShape));
        loadRangeSpinner(element, view.findViewById(R.id.SRange));
        loadControlLayerSpinner(element, view.findViewById(R.id.SControlLayer));
        loadLayerActionSpinner(element, view.findViewById(R.id.SLayerAction));
        loadSimpleSpinner(view.findViewById(R.id.SStickMode), new String[]{"Fixed", "Floating", "Follow Thumb"}, element.getStickMode().ordinal(), p -> element.setStickMode(ControlElement.StickMode.values()[p]));
        loadSimpleSpinner(view.findViewById(R.id.SMouseMode), new String[]{"Relative Mouse", "Camera Look", "Direct Touch"}, element.getMouseMode().ordinal(), p -> element.setMouseMode(ControlElement.MouseMode.values()[p]));
        loadSimpleSpinner(view.findViewById(R.id.SExpandGroup), new String[]{"None", "Group 1", "Group 2", "Group 3", "Group 4"}, element.getExpandGroup(), element::setExpandGroup);
        loadSimpleSpinner(view.findViewById(R.id.SExpandAction), new String[]{"None", "Toggle Group 1", "Toggle Group 2", "Toggle Group 3", "Toggle Group 4"}, element.getExpandAction().ordinal(), p -> element.setExpandAction(ControlElement.ExpandAction.values()[p]));

        setupSeekBar(view.findViewById(R.id.SBHitboxScale), Math.round(element.getHitboxScale()*100), p -> element.setHitboxScale(p/100f));
        setupSeekBar(view.findViewById(R.id.SBIdleOpacity), Math.round(element.getIdleOpacity()*100), p -> element.setIdleOpacity(p/100f));
        setupSeekBar(view.findViewById(R.id.SBActiveOpacity), Math.round(element.getActiveOpacity()*100), p -> element.setActiveOpacity(p/100f));
        setupSeekBar(view.findViewById(R.id.SBIconScale), Math.round(element.getIconScale()*100), p -> element.setIconScale(p/100f));
        setupSeekBar(view.findViewById(R.id.SBDeadZone), Math.round(element.getStickDeadZone()*100), p -> element.setStickDeadZone(p/100f));
        setupSeekBar(view.findViewById(R.id.SBMouseSensitivity), Math.round(element.getMouseSensitivity()*100), p -> element.setMouseSensitivity(p/100f));
        setupSeekBar(view.findViewById(R.id.SBMouseWidth), Math.round(element.getMouseAreaWidth()*100), p -> element.setMouseAreaWidth(p/100f));
        setupSeekBar(view.findViewById(R.id.SBMouseHeight), Math.round(element.getMouseAreaHeight()*100), p -> element.setMouseAreaHeight(p/100f));
        CheckBox cbLocked = view.findViewById(R.id.CBEditorLocked);
        cbLocked.setChecked(element.isEditorLocked());
        cbLocked.setOnCheckedChangeListener((button, checked) -> { element.setEditorLocked(checked); profile.save(); });

        RadioGroup rgOrientation = view.findViewById(R.id.RGOrientation);
        rgOrientation.check(element.getOrientation() == 1 ? R.id.RBVertical : R.id.RBHorizontal);
        rgOrientation.setOnCheckedChangeListener((group, checkedId) -> {
            element.setOrientation((byte)(checkedId == R.id.RBVertical ? 1 : 0));
            profile.save();
            inputControlsView.invalidate();
        });

        NumberPicker npColumns = view.findViewById(R.id.NPColumns);
        npColumns.setValue(element.getBindingCount());
        npColumns.setOnValueChangeListener((numberPicker, value) -> {
            element.setBindingCount(value);
            profile.save();
            inputControlsView.invalidate();
        });

        final TextView tvScale = view.findViewById(R.id.TVScale);
        SeekBar sbScale = view.findViewById(R.id.SBScale);
        sbScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvScale.setText(progress+"%");
                if (fromUser) {
                    progress = (int)Mathf.roundTo(progress, 5);
                    seekBar.setProgress(progress);
                    element.setScale(progress / 100.0f);
                    profile.save();
                    inputControlsView.invalidate();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbScale.setProgress((int)(element.getScale() * 100));

        CheckBox cbToggleSwitch = view.findViewById(R.id.CBToggleSwitch);
        cbToggleSwitch.setChecked(element.isToggleSwitch());
        cbToggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            element.setToggleSwitch(isChecked);
            profile.save();
        });

        final EditText etCustomText = view.findViewById(R.id.ETCustomText);
        etCustomText.setText(element.getText());
        final LinearLayout llIconList = view.findViewById(R.id.LLIconList);
        loadIcons(llIconList, element.getIconId());

        updateLayout.run();

        PopupWindow popupWindow = AppUtils.showPopupWindow(anchorView, view, 340, 0);
        popupWindow.setOnDismissListener(() -> {
            String text = etCustomText.getText().toString().trim();
            int iconId = 0;
            for (int i = 0; i < llIconList.getChildCount(); i++) {
                View child = llIconList.getChildAt(i);
                if (child.isSelected()) {
                    iconId = (int)child.getTag();
                    break;
                }
            }

            element.setText(text);
            element.setIconId(iconId);
            profile.save();
            inputControlsView.invalidate();
        });
    }

    private interface SelectionSetter { void set(int position); }

    private void loadSimpleSpinner(Spinner spinner, String[] labels, int selection, SelectionSetter setter) {
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setSelection(selection, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setter.set(position); profile.save(); inputControlsView.invalidate();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupSeekBar(SeekBar seekBar, int progress, SelectionSetter setter) {
        seekBar.setProgress(progress);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) { setter.set(value); profile.save(); inputControlsView.invalidate(); }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private void loadControlLayerSpinner(final ControlElement element, Spinner spinner) {
        String[] labels = {
            getString(R.string.controls_always_visible),
            getString(R.string.controls_layer_1),
            getString(R.string.controls_layer_2),
            getString(R.string.controls_layer_3)
        };
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setSelection(element.getLayer() + 1, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int layer = position - 1;
                if (layer != element.getLayer()) {
                    element.setLayer(layer);
                    profile.save();
                    inputControlsView.refreshLayerVisibility();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadLayerActionSpinner(final ControlElement element, Spinner spinner) {
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ControlElement.LayerAction.names()));
        spinner.setSelection(element.getLayerAction().ordinal(), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ControlElement.LayerAction action = ControlElement.LayerAction.values()[position];
                if (action != element.getLayerAction()) {
                    element.setLayerAction(action);
                    profile.save();
                    inputControlsView.refreshLayerVisibility();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadTypeSpinner(final ControlElement element, Spinner spinner, Runnable callback) {
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ControlElement.Type.names()));
        spinner.setSelection(element.getType().ordinal(), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ControlElement.Type type = ControlElement.Type.values()[position];
                if (type != element.getType()) {
                    element.setType(type);
                    profile.save();
                    callback.run();
                    inputControlsView.invalidate();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadShapeSpinner(final ControlElement element, Spinner spinner) {
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ControlElement.Shape.names()));
        spinner.setSelection(element.getShape().ordinal(), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                element.setShape(ControlElement.Shape.values()[position]);
                profile.save();
                inputControlsView.invalidate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadBindingSpinners(ControlElement element, View view) {
        LinearLayout container = view.findViewById(R.id.LLBindings);
        container.removeAllViews();

        ControlElement.Type type = element.getType();
        if (type == ControlElement.Type.BUTTON) {
            loadBindingSpinner(element, container, 0, R.string.binding);
            loadBindingSpinner(element, container, 1, R.string.combo_binding_1);
            loadBindingSpinner(element, container, 2, R.string.combo_binding_2);
            loadBindingSpinner(element, container, 3, R.string.combo_binding_3);
        }
        else if (type == ControlElement.Type.D_PAD || type == ControlElement.Type.STICK || type == ControlElement.Type.TRACKPAD) {
            loadBindingSpinner(element, container, 0, R.string.binding_up);
            loadBindingSpinner(element, container, 1, R.string.binding_right);
            loadBindingSpinner(element, container, 2, R.string.binding_down);
            loadBindingSpinner(element, container, 3, R.string.binding_left);
        }
    }

    private void loadBindingSpinner(final ControlElement element, LinearLayout container, final int index, int titleResId) {
        View view = LayoutInflater.from(this).inflate(R.layout.binding_field, container, false);
        ((TextView)view.findViewById(R.id.TVTitle)).setText(titleResId);
        final Spinner sBindingType = view.findViewById(R.id.SBindingType);
        final Spinner sBinding = view.findViewById(R.id.SBinding);

        Runnable update = () -> {
            String[] bindingEntries = null;
            switch (sBindingType.getSelectedItemPosition()) {
                case 0:
                    bindingEntries = Binding.keyboardBindingLabels();
                    break;
                case 1:
                    bindingEntries = Binding.mouseBindingLabels();
                    break;
                case 2:
                    bindingEntries = Binding.gamepadBindingLabels();
                    break;
            }

            sBinding.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, bindingEntries));
            AppUtils.setSpinnerSelectionFromValue(sBinding, element.getBindingAt(index).toString());
        };

        sBindingType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                update.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        Binding selectedBinding = element.getBindingAt(index);
        if (selectedBinding.isKeyboard()) {
            sBindingType.setSelection(0, false);
        }
        else if (selectedBinding.isMouse()) {
            sBindingType.setSelection(1, false);
        }
        else if (selectedBinding.isGamepad()) {
            sBindingType.setSelection(2, false);
        }

        sBinding.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Binding binding = Binding.NONE;
                switch (sBindingType.getSelectedItemPosition()) {
                    case 0:
                        binding = Binding.keyboardBindingValues()[position];
                        break;
                    case 1:
                        binding = Binding.mouseBindingValues()[position];
                        break;
                    case 2:
                        binding = Binding.gamepadBindingValues()[position];
                        break;
                }

                if (binding != element.getBindingAt(index)) {
                    element.setBindingAt(index, binding);
                    profile.save();
                    inputControlsView.invalidate();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        update.run();
        container.addView(view);
    }

    private void loadRangeSpinner(final ControlElement element, Spinner spinner) {
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ControlElement.Range.names()));
        spinner.setSelection(element.getRange().ordinal(), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                element.setRange(ControlElement.Range.values()[position]);
                profile.save();
                inputControlsView.invalidate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadIcons(final LinearLayout parent, int selectedId) {
        List<Integer> iconIds = new ArrayList<>();
        try {
            String[] filenames = getAssets().list("inputcontrols/icons/");
            if (filenames != null) for (String filename : filenames) {
                String name = FileUtils.getBasename(filename);
                if (name.matches("\\d+")) iconIds.add(Integer.parseInt(name));
            }
        }
        catch (IOException e) {}

        iconIds.sort(Integer::compareTo);

        int size = (int)UnitUtils.dpToPx(40);
        int margin = (int)UnitUtils.dpToPx(2);
        int padding = (int)UnitUtils.dpToPx(4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(margin, 0, margin, 0);

        for (final int id : iconIds) {
            ImageView imageView = new ImageView(this);
            imageView.setLayoutParams(params);
            imageView.setPadding(padding, padding, padding, padding);
            imageView.setBackgroundResource(R.drawable.icon_background);
            imageView.setTag(id);
            imageView.setSelected(id == selectedId);
            imageView.setOnClickListener((v) -> {
                for (int i = 0; i < parent.getChildCount(); i++) parent.getChildAt(i).setSelected(false);
                imageView.setSelected(true);
            });

            try (InputStream is = getAssets().open("inputcontrols/icons/"+id+".png")) {
                imageView.setImageBitmap(BitmapFactory.decodeStream(is));
            }
            catch (IOException e) {}

            parent.addView(imageView);
        }
    }
}
