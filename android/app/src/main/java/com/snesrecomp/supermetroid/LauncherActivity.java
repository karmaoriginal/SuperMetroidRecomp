package com.snesrecomp.supermetroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Android-native library screen. The user's ROM is copied into app-private
 * storage; it is never bundled into the APK or uploaded anywhere. */
public final class LauncherActivity extends Activity {
    private static final int PICK_ROM = 10;
    private static final int PICK_MOD = 11;
    private static final int PICK_TEXTURE = 12;
    static final String ROM_NAME = "super_metroid.sfc";
    private static final String EXPECTED_SHA256 =
            "12b77c4bc9c1832cee8881244659065ee1d84c70c3d29e6eaf92e6798cc2ca72";

    private LinearLayout content;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        showLibrary();
    }

    private File storage() {
        File f = getExternalFilesDir(null);
        return f != null ? f : getFilesDir();
    }

    static File romFile(Activity activity) {
        File f = activity.getExternalFilesDir(null);
        if (f == null) f = activity.getFilesDir();
        return new File(f, ROM_NAME);
    }

    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 12, 0, 0);
        b.setLayoutParams(p);
        return b;
    }

    private void showLibrary() {
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(48, 48, 48, 48);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(content);

        TextView title = new TextView(this);
        title.setText("SUPER METROID\nRECOMPILED");
        title.setGravity(Gravity.CENTER);
        title.setTextSize(28);
        title.setTextColor(0xffffc107);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView legal = new TextView(this);
        legal.setText("Usa tu copia legal sin modificar de Super Metroid (Japan, USA). " +
                "La ROM permanece en este dispositivo y se usa para cargar los assets del juego.");
        legal.setTextSize(15);
        legal.setPadding(0, 24, 0, 12);
        content.addView(legal);

        final File rom = romFile(this);
        if (rom.isFile()) {
            content.addView(button("▶  Jugar", v -> {
                startActivity(new Intent(this, GameActivity.class));
                finish();
            }));
            content.addView(button("Cambiar ROM", v -> pick(PICK_ROM, "*/*", false)));
        } else {
            content.addView(button("＋  Añadir ROM", v -> pick(PICK_ROM, "*/*", false)));
        }
        content.addView(button("Instalar mod (.snesmod)", v -> pick(PICK_MOD, "*/*", true)));
        content.addView(button("Importar pack de texturas", v -> pick(PICK_TEXTURE, "application/zip", true)));
        content.addView(button("Gestionar contenido", v -> showContentManager()));
        setContentView(scroll);
    }

    private void pick(int request, String type, boolean multiple) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(type);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
        startActivityForResult(i, request);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        try {
            if (data.getClipData() != null) {
                for (int n = 0; n < data.getClipData().getItemCount(); n++)
                    importUri(request, data.getClipData().getItemAt(n).getUri());
            } else if (data.getData() != null) {
                importUri(request, data.getData());
            }
            showLibrary();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo importar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importUri(int request, Uri uri) throws Exception {
        String name = displayName(uri);
        File destination;
        if (request == PICK_ROM) {
            destination = romFile(this);
        } else {
            File dir = new File(storage(), request == PICK_MOD ? "mods/inbox" : "textures");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("no se pudo crear " + dir);
            destination = new File(dir, safeName(name));
        }
        File temporary = new File(destination.getParentFile(), destination.getName() + ".part");
        MessageDigest digest = request == PICK_ROM ? MessageDigest.getInstance("SHA-256") : null;
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(temporary)) {
            if (in == null) throw new Exception("archivo no legible");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = in.read(buffer)) >= 0) {
                out.write(buffer, 0, count);
                if (digest != null) digest.update(buffer, 0, count);
            }
        }
        if (digest != null && !hex(digest.digest()).equals(EXPECTED_SHA256)) {
            temporary.delete();
            throw new Exception("la ROM no es la revisión compatible o tiene cabecera/parches");
        }
        if (request == PICK_MOD && !name.toLowerCase(Locale.ROOT).endsWith(".snesmod")) {
            temporary.delete();
            throw new Exception("los mods deben tener extensión .snesmod");
        }
        if (destination.exists() && !destination.delete()) throw new Exception("no se pudo reemplazar el archivo");
        if (!temporary.renameTo(destination)) throw new Exception("no se pudo guardar el archivo");
        Toast.makeText(this, request == PICK_ROM ? "ROM verificada" : "Contenido importado", Toast.LENGTH_SHORT).show();
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (col >= 0) return c.getString(col);
            }
        }
        return "imported-" + System.currentTimeMillis();
    }

    private static String safeName(String name) {
        String clean = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        return clean.isEmpty() ? "imported" : clean;
    }

    private static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder();
        for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 255));
        return s.toString();
    }

    private void showContentManager() {
        File pendingMods = new File(storage(), "mods/inbox");
        File installedMods = new File(storage(), "mods/preloaded/packages");
        File textures = new File(storage(), "textures");
        String message = "Mods instalados: " + countFiles(installedMods) +
                "\nMods pendientes: " + countFiles(pendingMods) +
                "\nPacks de texturas: " + countFiles(textures) +
                "\n\nLos .snesmod se validan al iniciar el juego y quedan disponibles en el siguiente inicio. " +
                "Los packs de texturas se conservan para mods compatibles.";
        new AlertDialog.Builder(this).setTitle("Contenido").setMessage(message)
                .setNeutralButton("Borrar pendientes", (d, w) -> { deleteChildren(pendingMods); showLibrary(); })
                .setNegativeButton("Borrar texturas", (d, w) -> { deleteChildren(textures); showLibrary(); })
                .setPositiveButton("Cerrar", null).show();
    }

    private static int countFiles(File dir) {
        File[] files = dir.listFiles();
        return files == null ? 0 : files.length;
    }

    private static void deleteChildren(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (f.isFile()) f.delete();
    }
}
