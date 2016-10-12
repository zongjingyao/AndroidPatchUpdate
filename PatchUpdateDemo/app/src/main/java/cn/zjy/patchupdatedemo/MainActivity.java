package cn.zjy.patchupdatedemo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import cn.zjy.patchupdate.PatchUpdate;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final EditText etPatchUrl = (EditText) findViewById(R.id.et_patch_url);
        Button btnDownload = (Button) findViewById(R.id.btn_download);
        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Toast.makeText(MainActivity.this, "DONE!", Toast.LENGTH_SHORT).show();
                PatchUpdate.update(MainActivity.this, etPatchUrl.getText().toString(), new PatchUpdate.PatchUpdateListener() {
                    @Override
                    public void onSuccess(final String newApk) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                PatchUpdate.install(MainActivity.this, newApk);
                            }
                        });
                    }

                    @Override
                    public void onFailure(final String error) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "failed: " + error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
        });
    }
}
