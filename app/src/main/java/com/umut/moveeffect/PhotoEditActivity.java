package com.umut.moveeffect;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.umut.moveeffect.misc.SeekProgressChangeListener;
import com.umut.moveeffect.util.BitmapUtils;
import com.umut.moveeffect.util.Constants;
import com.umut.moveeffect.util.FileUtils;
import com.umut.moveeffect.view.EditableImageView;
import com.umut.moveeffect.view.SelectionStateListener;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class PhotoEditActivity extends AppCompatActivity implements SelectionStateListener {

    private static final int PHOTO_SELECTION_RC = 1000;
    private static final int WRITE_EXTERNAL_RC = 1001;
    private static final String MIME_TYPE_GALLERY_PICK = "image/*";

    private EditableImageView imageView;
    private TextView repCountTextView;
    private TextView alphaIndicatorTextView;
    private SeekBar repCountSeekBar;
    private SeekBar alphaSelectionSeekBar;
    private View settingsSelectionView;

    private float imageViewWidth;
    private float imageViewHeight;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_edit);
        initViews();
        computeImageViewSize();
        startPhotoSelection();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case PHOTO_SELECTION_RC:
                if (resultCode == RESULT_OK) {
                    initEditViewWithImage(data);
                }
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case WRITE_EXTERNAL_RC:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    doSaveImage();
                } else {
                    Toast.makeText(getApplicationContext(), "Permission Denied", Toast.LENGTH_LONG).show();
                }
                return;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.selectFromGalleryMenuItem:
                startPhotoSelection();
                return true;
            case R.id.saveImageMenuItem:
                saveImage();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // SelectionStateListener Methods [START]
    @Override
    public void onAreaSelect() {
        settingsSelectionView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onAreaSelectionReleased() {
        settingsSelectionView.setVisibility(View.INVISIBLE);
    }
    // SelectionStateListener Methods [END]

    // [Private Methods]
    private void initViews() {
        imageView = findViewById(R.id.editableImageView);
        imageView.setSelectionStateChangeListener(this);
        repCountTextView = findViewById(R.id.repCountIndicatorTextView);
        alphaIndicatorTextView = findViewById(R.id.alphaIndicatorTextView);
        repCountSeekBar = findViewById(R.id.repCountSeekBar);
        alphaSelectionSeekBar = findViewById(R.id.alphaSelectionSeekBar);
        settingsSelectionView = findViewById(R.id.moveSelectionWrapperRelativeLayout);
        prepareAlphaSelection();
        prepareRepCountSelection();
    }

    private void prepareRepCountSelection() {
        repCountSeekBar.setMax(Constants.MAX_REP_COUNT - Constants.MIN_REP_COUNT);
        repCountSeekBar.setOnSeekBarChangeListener(new SeekProgressChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                final int repCount = progress + Constants.MIN_REP_COUNT;
                imageView.updateRepCount(repCount);
                repCountTextView.setText(String.valueOf(repCount));
            }
        });
        repCountSeekBar.setProgress(Constants.DEFAULT_RED_COUNT);
    }

    private void prepareAlphaSelection() {
        alphaSelectionSeekBar.setMax(Constants.MAX_ALPHA - Constants.MIN_ALPHA);
        alphaSelectionSeekBar.setOnSeekBarChangeListener(new SeekProgressChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                final int alpha = progress + Constants.MIN_ALPHA;
                imageView.updateAlpha(alpha);
                alphaIndicatorTextView.setText(String.valueOf(alpha));
            }
        });
        alphaSelectionSeekBar.setProgress(Constants.DEFAULT_ALPHA);
    }

    private void startPhotoSelection() {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(MIME_TYPE_GALLERY_PICK);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.picture_selector_title)), PHOTO_SELECTION_RC);
    }

    private void computeImageViewSize() {
        imageView.post(() -> {
                    this.imageViewWidth = imageView.getWidth();
                    this.imageViewHeight = imageView.getHeight();
                }
        );
    }

    private void initEditViewWithImage(@Nullable Intent data) {
        if (data == null) {
            return;
        }
        final Uri imageUri = data.getData();
        if (imageUri != null) {
            try {
                final InputStream is = getContentResolver().openInputStream(imageUri);
                final Bitmap resizedBitmap = BitmapUtils.resize(BitmapFactory.decodeStream(is),
                        imageViewWidth, imageViewHeight);
                if (resizedBitmap != null) {
                    if (resizedBitmap.getWidth() != imageView.getWidth() ||
                            resizedBitmap.getHeight() != imageView.getHeight()) {
                        final ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
                        layoutParams.width = resizedBitmap.getWidth();
                        layoutParams.height = resizedBitmap.getHeight();
                        imageView.setLayoutParams(layoutParams);
                    }
                }
                imageView.setImageBitmap(resizedBitmap);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveImage() {
        if (ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            doSaveImage();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_RC);
        }
    }

    private void doSaveImage() {
        final Bitmap drawnBitmap = imageView.getDrawnBitmap();
        if (drawnBitmap != null) {
            String savedFile = FileUtils.saveBitmap(drawnBitmap);
            if (savedFile == null) {
                Toast.makeText(getApplicationContext(), "Problem saving image", Toast.LENGTH_SHORT).show();
            } else {
                MediaScannerConnection.scanFile(this, new String[]{savedFile},
                        null, null);
                Toast.makeText(getApplicationContext(), "Image Saved!", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "Save after modification", Toast.LENGTH_SHORT).show();
        }
    }
}
