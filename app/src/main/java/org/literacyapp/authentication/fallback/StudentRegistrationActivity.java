package org.literacyapp.authentication.fallback;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.skyfishjy.library.RippleBackground;

import org.literacyapp.LiteracyApplication;
import org.literacyapp.R;
import org.literacyapp.contentprovider.dao.StudentDao;
import org.literacyapp.contentprovider.dao.StudentImageDao;
import org.literacyapp.contentprovider.model.Student;
import org.literacyapp.receiver.ScreenOnReceiver;
import org.literacyapp.util.MediaPlayerHelper;
import org.literacyapp.util.StudentHelper;
import org.literacyapp.util.StudentUpdateHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;

/**
 * Handles creation of a new student (image + storage in database).
 */
public class StudentRegistrationActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private RippleBackground rippleBackground;

    private Button cameraButton;

    private ImageView thumbnailImageView;

    private Bitmap imageBitmap;

    private ImageView checkmarkImageView;

    private StudentImageDao studentImageDao;

    private StudentDao studentDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(getClass().getName(), "onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_registration);

        rippleBackground = (RippleBackground) findViewById(R.id.studentRegistrationRippleBackground);
        cameraButton = (Button) findViewById(R.id.studentRegistrationButton);
        thumbnailImageView = (ImageView) findViewById(R.id.studentRegistrationImageView);
        checkmarkImageView = (ImageView) findViewById(R.id.studentRegistrationCheckmark);

        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(getClass().getName(), "cameraButton onClick");

                // Take picture with front camera. See https://developer.android.com/training/camera/photobasics.html
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);

                    cameraButton.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            MediaPlayerHelper.play(getApplicationContext(), R.raw.auth_fallback_registration_picture);
                        }
                    }, 1000);
                }
            }
        });

        LiteracyApplication literacyApplication = (LiteracyApplication) getApplication();
        studentImageDao = literacyApplication.getDaoSession().getStudentImageDao();
        studentDao = literacyApplication.getDaoSession().getStudentDao();
    }

    @Override
    protected void onStart() {
        Log.i(getClass().getName(), "onStart");
        super.onStart();

        if (imageBitmap == null) {
            cameraButton.postDelayed(new Runnable() {
                @Override
                public void run() {
                    MediaPlayerHelper.play(getApplicationContext(), R.raw.auth_fallback_registration_button);
                }
            }, 1000);

            // Play an animation to indicate that the cameraButton should be pressed
            cameraButton.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.i(getClass().getName(), "cameraButton.postDelayed run");

                    final long duration = 300;

                    final ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(cameraButton, View.SCALE_X, 1f, 1.2f, 1f);
                    scaleXAnimator.setDuration(duration);

                    final ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(cameraButton, View.SCALE_Y, 1f, 1.2f, 1f);
                    scaleYAnimator.setDuration(duration);

                    scaleXAnimator.start();
                    scaleYAnimator.start();

                    final AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.play(scaleXAnimator).with(scaleYAnimator);
                    animatorSet.start();

                    rippleBackground.startRippleAnimation();
                    rippleBackground.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            rippleBackground.stopRippleAnimation();
                        }
                    }, 600);
                }
            }, 5000);
        } else {
            // Replace camera button with image thumbnail
            cameraButton.setVisibility(View.GONE);

            // Pulsate image thumbnail
            rippleBackground.startRippleAnimation();
            rippleBackground.postDelayed(new Runnable() {
                @Override
                public void run() {
                    rippleBackground.stopRippleAnimation();
                }
            }, 600);

            // Animate checkmark
            checkmarkImageView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkmarkImageView.setVisibility(View.VISIBLE);
                    Drawable drawable = checkmarkImageView.getDrawable();
                    ((Animatable) drawable).start();

                    MediaPlayerHelper.play(getApplicationContext(), R.raw.level_up);

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            MediaPlayerHelper.play(getApplicationContext(), R.raw.auth_fallback_registration_complete);

                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    finish();
                                }
                            }, 2000);
                        }
                    }, 1000);
                }
            }, 600);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(getClass().getName(), "onActivityResult");
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == REQUEST_IMAGE_CAPTURE) && (resultCode == RESULT_OK)) {
            Bundle extras = data.getExtras();
            imageBitmap = (Bitmap) extras.get("data"); // 180x135px
            // TODO: get full-size image
            // TODO: detect face(s) in image
            thumbnailImageView.setImageBitmap(imageBitmap);

            // Store image on SD card
            String uniqueId = StudentHelper.generateNextUniqueId(getApplicationContext(), studentDao);
            Log.i(getClass().getName(), "uniqueId: " + uniqueId);
            String imageFilePath = StudentHelper.getStudentAvatarDirectory() + "/" + uniqueId + ".png";
            Log.i(getClass().getName(), "Storing image at " + imageFilePath);
            File scaledScreenshotFile = new File(imageFilePath);
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(scaledScreenshotFile);
                imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
                fileOutputStream.close();

                // Set Student skill level
                // TODO: if this is the first Student being registered on the device, set skill level to match previous learning progress stored on Device, and assign all previous events to Student.

                // Store Student in database
                Student student = new Student();
                student.setUniqueId(uniqueId);
                student.setTimeCreated(Calendar.getInstance());
                student.setAvatar(imageFilePath);
                Log.i(getClass().getName(), "Storing Student in database");
                studentDao.insert(student);
                Log.i(getClass().getName(), "Student stored in database with id " + student.getId());

                // Store time of last successful authentication
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                sharedPreferences.edit().putLong(ScreenOnReceiver.PREF_TIME_OF_LAST_AUTHENTICATION, Calendar.getInstance().getTimeInMillis()).commit();

                // Personalize apps/content according to Student's level
                new StudentUpdateHelper(getApplicationContext(), student).updateStudent();
            } catch (FileNotFoundException e) {
                Log.e(getClass().getName(), null, e);
            } catch (IOException e) {
                Log.e(getClass().getName(), null, e);
            }
        }
    }
}
