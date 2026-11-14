package com.snesrecomp.supermetroid;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.libsdl.app.SDLActivity;

/** SDL game host plus an Android-native, multi-touch controller overlay. */
public final class GameActivity extends SDLActivity {
    private TouchControlsView controls;

    @Override protected String[] getArguments() {
        return new String[] { "--no-launcher", "--rom", LauncherActivity.romFile(this).getAbsolutePath() };
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        controls = new TouchControlsView(this);
        addContentView(controls, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        controls.setSettingsListener(this::showTouchSettings);
    }

    private void showTouchSettings() {
        FrameLayout panel = new FrameLayout(this);
        android.widget.LinearLayout rows = new android.widget.LinearLayout(this);
        rows.setOrientation(android.widget.LinearLayout.VERTICAL);
        rows.setPadding(42, 12, 42, 0);
        panel.addView(rows);

        TextView opacityLabel = new TextView(this);
        opacityLabel.setText("Opacidad"); rows.addView(opacityLabel);
        SeekBar opacity = new SeekBar(this); opacity.setMax(100);
        opacity.setProgress((int)(controls.getControlOpacity() * 100)); rows.addView(opacity);

        TextView scaleLabel = new TextView(this);
        scaleLabel.setText("Tamaño"); rows.addView(scaleLabel);
        SeekBar scale = new SeekBar(this); scale.setMax(100);
        scale.setProgress((int)((controls.getControlScale() - .55f) / .0095f)); rows.addView(scale);

        new AlertDialog.Builder(this).setTitle("Controles táctiles")
                .setMessage("Activa Editar para arrastrar cada botón. El diseño, tamaño y opacidad se guardan automáticamente.")
                .setView(panel)
                .setPositiveButton(controls.isEditing() ? "Terminar edición" : "Editar posición", (d, w) -> {
                    controls.setEditing(!controls.isEditing());
                    Toast.makeText(this, controls.isEditing() ? "Arrastra los controles" : "Diseño guardado", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Restablecer", (d, w) -> controls.resetLayout())
                .setNegativeButton("Biblioteca", (d, w) -> {
                    startActivity(new Intent(this, LauncherActivity.class));
                    finish();
                }).setOnDismissListener(d -> {
                    controls.setControlOpacity(opacity.getProgress() / 100f);
                    controls.setControlScale(.55f + scale.getProgress() * .0095f);
                }).show();
    }
}
