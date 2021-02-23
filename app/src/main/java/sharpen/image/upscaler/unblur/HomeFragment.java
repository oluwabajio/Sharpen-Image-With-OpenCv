package sharpen.image.upscaler.unblur;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.codekidlabs.storagechooser.StorageChooser;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.opensooq.supernova.gligar.GligarPicker;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import sharpen.image.upscaler.unblur.databinding.FragmentHomeBinding;
import com.blankj.utilcode.util.FileIOUtils;
import static com.blankj.utilcode.util.UriUtils.uri2File;

public class HomeFragment extends Fragment {


    private static final int PICKER_REQUEST_CODE = 101;
    private static final int PERMISSION_REQUEST_CODE = 102;
    private static final String INTERSTITIAL_ADS_UNIT = "ca-app-pub-9562015878942760/6101952263";
    private FragmentHomeBinding binding;
    private Bitmap inputImageBitmap;
    private static final String TAG = "HomeFragment";
    private InterstitialAd mInterstitialAd;
    private String selectedImagePath;
    private Uri selectedImageURI;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        initializeAds();
        initListeners();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    private void initListeners() {
        binding.btnOpenFile.setOnClickListener(v -> {
            if (checkPermission()) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICKER_REQUEST_CODE);

            } else {
            }
        });

        binding.btnDownload.setOnClickListener(v -> {

            if (checkPermission()) {
                chooseLocationDialog();
            }

        });

        binding.btnSharpen.setOnClickListener(v -> sharpenImage());

        binding.btnShowOriginal.setOnClickListener(v -> {
            showOriginalImage();
        });
    }

    private void showOriginalImage() {
        if (inputImageBitmap != null) {
            binding.imgImage.setImageBitmap(inputImageBitmap);
            binding.btnShowOriginal.setVisibility(View.GONE);
            binding.btnSharpen.setVisibility(View.VISIBLE);
        }
    }

    private void sharpenImage() {
        Mat img = new Mat();
        Utils.bitmapToMat(inputImageBitmap, img);


        // Imgproc.cvtColor(img, img, Imgproc.COLOR_RGB2BGRA);
        Mat dest = new Mat(img.rows(), img.cols(), img.type());

        Imgproc.GaussianBlur(img, dest, new Size(0, 0), 10);
        Core.addWeighted(img, 1.5, dest, -0.5, 0, dest);

        Bitmap img_bitmap = Bitmap.createBitmap(dest.cols(), dest.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(dest, img_bitmap);
        binding.imgImage.setImageBitmap(img_bitmap);
        binding.btnShowOriginal.setVisibility(View.VISIBLE);
        binding.btnSharpen.setVisibility(View.GONE);
        int rotateImage = getCameraPhotoOrientation(selectedImageURI, selectedImagePath);

        binding.imgImage.setRotation(rotateImage);
    }

    private void chooseLocationDialog() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImage("", "jpg");

        } else {
            StorageChooser chooser = new StorageChooser.Builder().withActivity(getActivity()).withFragmentManager(getActivity().getFragmentManager()).withMemoryBar(true).allowCustomPath(true).setType(StorageChooser.DIRECTORY_CHOOSER).build();
            chooser.show();

// get path that the user has chosen
            chooser.setOnSelectListener(new StorageChooser.OnSelectListener() {
                @Override
                public void onSelect(String path) {
                    saveImage(path, "jpg");
                }
            });
        }
    }

    private void saveImage(String path, String extension) {


        binding.imgImage.setDrawingCacheEnabled(true);
        Bitmap b = binding.imgImage.getDrawingCache();


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            OutputStream out2 = null;
            String filename = new SimpleDateFormat("yyyyMM_dd-HHmmss").format(new Date()) + "." + extension;
            String mimeType = "image/jpeg";
            String directory = Environment.DIRECTORY_PICTURES;
            Uri mediaContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
            values.put(MediaStore.Images.Media.RELATIVE_PATH, directory);


            try {
                Uri uri = getActivity().getContentResolver().insert(mediaContentUri, values);
                out2 = getActivity().getContentResolver().openOutputStream(uri);
                showDownloadSavedDialog("Image Gallery (Go to Gallery/Pictures)");
            } catch (Exception e) {
                Toast.makeText(getActivity(), "Error: ", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "saveImage: Exception Error: " + e.getMessage());
            }
            b.compress(Bitmap.CompressFormat.JPEG, 90, out2);
            //Opening an outputstream with the Uri that we got

        } else {

            try {
                String imgPath = path + "/" + new SimpleDateFormat("yyyyMM_dd-HHmmss").format(new Date()) + "." + extension;
                File file = new File(imgPath);

                FileOutputStream out = new FileOutputStream(file);
                b.compress(Bitmap.CompressFormat.JPEG, 90, out);
                out.flush();
                out.close();
                showDownloadSavedDialog(path);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


    }

    private void showDownloadSavedDialog(String path) {
        new AlertDialog.Builder(getActivity()).setTitle("IMAGE SAVED SUCCESSFULLY!!").setMessage("Your image has been saved to \n " + path)

                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        showInterstitialAd();
                    }
                })

                .setIcon(android.R.drawable.ic_dialog_info).show();
    }


    public int getCameraPhotoOrientation(Uri imageUri, String imagePath) {
        int rotate = 0;
        try {
            getActivity().getContentResolver().notifyChange(imageUri, null);
            File imageFile = new File(imagePath);
            ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }

            Log.i("RotateImage", "Exif orientation: " + orientation);
            Log.i("RotateImage", "Rotate value: " + rotate);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rotate;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case PICKER_REQUEST_CODE: {
                Uri selectedImageURI = data.getData();
                String imagePath =  uri2File(selectedImageURI).getAbsolutePath();
                Log.e(TAG, "onActivityResult: Image file path = " + imagePath);
                this.selectedImageURI = selectedImageURI;
                selectedImagePath = imagePath;
                binding.imgImage.setImageURI(selectedImageURI);
                binding.btnShowOriginal.setVisibility(View.GONE);
                binding.btnSharpen.setVisibility(View.VISIBLE);
                binding.imgImage.setRotation(0);

                try {
                    inputImageBitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), selectedImageURI);
                    inputImageBitmap = inputImageBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    binding.ly2.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    Toast.makeText(getActivity(), "Exception = " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }


                break;
            }
        }
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        // request permission if it has not been grunted.
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private void showInterstitialAd() {
        if (mInterstitialAd.isLoaded()) {
            mInterstitialAd.show();
        } else {
            Log.e("TAG", "The interstitial wasn't loaded yet.");
        }
    }

    private void initializeAds() {
        MobileAds.initialize(getActivity());

        mInterstitialAd = new InterstitialAd(getActivity());
        mInterstitialAd.setAdUnitId(INTERSTITIAL_ADS_UNIT);
        mInterstitialAd.loadAd(new AdRequest.Builder().build());


        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                // Code to be executed when an ad finishes loading.
            }

            @Override
            public void onAdOpened() {
                // Code to be executed when the ad is displayed.
            }

            @Override
            public void onAdClicked() {
                // Code to be executed when the user clicks on an ad.
            }

            @Override
            public void onAdLeftApplication() {
                // Code to be executed when the user has left the app.
            }

            @Override
            public void onAdClosed() {
                // Code to be executed when the interstitial ad is closed.
                initializeAds();
            }
        });


    }


    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (checkPermission()) {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICKER_REQUEST_CODE);

        }
    }
}