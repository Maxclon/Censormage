package com.maxclon.censormage;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private static final int PICK_MEDIA_REQUEST = 1;
    private static final int REQUEST_PERMISSIONS = 123;
    private static final String AUTHORITY = "com.maxclon.censormage.fileprovider";
    private static final int MEDIA_TYPE_IMAGE = 1;
    private static final int MEDIA_TYPE_VIDEO = 2;
    private GestureDetector gestureDetector;
    private ImageView imageView;
    private VideoView videoView;
    private Button openMediaButton;
    private Button guardarButton;
    private Button tramadoButton;
    private EditText grosorEditText;
    private EditText separadorEditText;
    private Uri mediaUri;
    private Bitmap originalImage;
    private Uri outputMediaFileUri;

    private float scaleFactor = 1.0f;
    private ScaleGestureDetector scaleGestureDetector;

    private float lastTouchX;
    private float lastTouchY;
    private float posX = 0;
    private float posY = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(getResources().getColor(R.color.colorPrimaryDark));
        }
        setContentView(R.layout.activity_main);
        initializeViewsAndClickListeners();
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());
        checkAndRequestPermissions();
        gestureDetector = new GestureDetector(this, new GestureListener());
    }

    private void initializeViewsAndClickListeners() {
        imageView = findViewById(R.id.CM_VISOR);
        videoView = findViewById(R.id.CM_VIDEO_VISOR);
        openMediaButton = findViewById(R.id.CM_ABRIR_MEDIA);
        guardarButton = findViewById(R.id.CM_GUARDAR);
        tramadoButton = findViewById(R.id.CM_TRAMADO);
        grosorEditText = findViewById(R.id.CM_GROSOR_T);
        separadorEditText = findViewById(R.id.CM_SEPARADOR_T);

        openMediaButton.setOnClickListener(v -> openMediaPicker());
        guardarButton.setOnClickListener(v -> {
            if (mediaUri != null) {
                String mediaType = getMediaType(mediaUri);
                if (mediaType.equals("image")) {
                    saveImage();
                } else if (mediaType.equals("video")) {
                    saveVideo();
                } else {
                    Toast.makeText(this, "Selecciona una imagen o video antes de guardar", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Selecciona una imagen o video antes de guardar", Toast.LENGTH_SHORT).show();
            }
        });
        tramadoButton.setOnClickListener(v -> {
            if (mediaUri != null) {
                String mediaType = getMediaType(mediaUri);
                if (mediaType.equals("image")) {
                    applyHatchPatternEffect();
                } else if (mediaType.equals("video")) {
                    extractVideoFramesAndApplyEffect();
                } else {
                    Toast.makeText(this, "Selecciona una imagen o video antes de aplicar el efecto", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Selecciona una imagen o video antes de aplicar el efecto", Toast.LENGTH_SHORT).show();
            }
        });


    }

    private void openMediaPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/* video/*");
        startActivityForResult(Intent.createChooser(intent, "Selecciona una imagen o video"), PICK_MEDIA_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_MEDIA_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            mediaUri = data.getData();
            String mediaType = getMediaType(mediaUri);
            if (mediaType.equals("image")) {
                imageView.setImageURI(mediaUri);
                imageView.setVisibility(View.VISIBLE);
                videoView.setVisibility(View.GONE);
                originalImage = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
            } else if (mediaType.equals("video")) {
                videoView.setVideoURI(mediaUri);

                MediaController mediaController = new MediaController(this);
                mediaController.setMediaPlayer(videoView);
                videoView.setMediaController(mediaController);

                // Agrega una vista de previsualización para evitar el fondo negro (o eso parece)
                videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                       
                        mediaPlayer.setDisplay(videoView.getHolder());

                       
                        videoView.start();
                    }
                });
                imageView.setVisibility(View.GONE); 
                videoView.setVisibility(View.VISIBLE); 
            }
        } else {
            imageView.setVisibility(View.GONE);
            videoView.setVisibility(View.GONE);
        }
    }

    private String getMediaType(Uri mediaUri) {
        String mimeType = getContentResolver().getType(mediaUri);
        if (mimeType != null) {
            if (mimeType.startsWith("image")) {
                return "image";
            } else if (mimeType.startsWith("video")) {
                return "video";
            }
        }
        return "unknown";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                Toast.makeText(this, "Permiso de escritura en almacenamiento denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSIONS);
        }
    }


    private void applyHatchPatternEffect() {
        if (originalImage != null) {
            float strokeWidth = 9.0f;
            String grosorStr = grosorEditText.getText().toString();
            int separador = 25;
            String separadorStr = separadorEditText.getText().toString();

            try {
                if (!grosorStr.isEmpty()) {
                    strokeWidth = Float.parseFloat(grosorStr);
                    if (strokeWidth < 0) {
                        Toast.makeText(this, "El grosor no puede ser negativo", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                if (!separadorStr.isEmpty()) {
                    separador = Integer.parseInt(separadorStr);
                    if (separador < 0) {
                        Toast.makeText(this, "El separador no puede ser negativo", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                Bitmap tramadoBitmap = applyHatchPattern(originalImage, strokeWidth, separador);
                imageView.setImageBitmap(tramadoBitmap);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                Toast.makeText(this, "Ingresa valores numéricos válidos en grosor y separador", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Carga una imagen antes de aplicar el efecto", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap applyHatchPattern(Bitmap sourceBitmap, float strokeWidth, int separador) {
        int width = sourceBitmap.getWidth();
        int height = sourceBitmap.getHeight();
        Bitmap hatchBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(hatchBitmap);
        Paint paint = new Paint();

        canvas.drawBitmap(sourceBitmap, 0, 0, null);

        for (int i = 0; i < width + height; i += separador) {
            float startX = (i < height) ? 0 : (i - height);
            float startY = (i < height) ? i : height;
            float endX = (i < width) ? i : width;
            float endY = (i < width) ? 0 : (i - width);

            paint.setColor(android.graphics.Color.argb(255, 0, 0, 0));
            paint.setStrokeWidth(strokeWidth);

            canvas.drawLine(startX, startY, endX, endY, paint);
        }

        return hatchBitmap;
    }


    private Bitmap combineBitmaps(Bitmap original, Bitmap hatchPattern) {
        int width = original.getWidth();
        int height = original.getHeight();
        Bitmap combinedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(combinedBitmap);
        canvas.drawBitmap(original, 0, 0, null);
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(hatchPattern, 0, 0, paint);
        return combinedBitmap;
    }

    private void saveImage() {
        if (imageView.getDrawable() != null) {
            Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

            ContentResolver resolver = getContentResolver();
            Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (imageUri != null) {
                try {
                    OutputStream outputStream = resolver.openOutputStream(imageUri);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    outputStream.close();
                    Toast.makeText(this, "Imagen guardada en la galería", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e("MainActivity", "Error al guardar la imagen: " + e.getMessage());
                    Toast.makeText(this, "Error al guardar la imagen", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "No se pudo guardar la imagen", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Carga una imagen antes de guardarla", Toast.LENGTH_SHORT).show();
        }
    }

    private void extractVideoFramesAndApplyEffect() {
        if (mediaUri != null) {
            String mediaType = getMediaType(mediaUri);
            int separador = 25;
            String separadorStr = separadorEditText.getText().toString();

            try {
                if (!separadorStr.isEmpty()) {
                    separador = Integer.parseInt(separadorStr);
                    if (separador < 0) {
                        Toast.makeText(this, "El separador no puede ser negativo", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                if (mediaType.equals("video")) {
                    String outputDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
                    String outputFileName = "processed_video.mp4";
                    String outputPath = outputDirectory + File.separator + outputFileName;

                    // Obtener la información del video original
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(mediaUri.getPath());
                    String originalFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
                    String originalWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                    String originalHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

                    // Configura el comando FFmpeg para extraer y procesar fotogramas (parcialmente funciona, pero da error)
                    String[] cmd = new String[]{
                            "-i", mediaUri.toString(), 
                            "-vf", "fps=" + originalFps + ",scale=" + originalWidth + ":" + originalHeight,
                            outputDirectory + File.separator + "frame%03d.jpg" 
                    };

                    int result = FFmpeg.execute(cmd);

                    if (result == Config.RETURN_CODE_SUCCESS) {
                       
                        List<String> framePaths = new ArrayList<>();
                        int i = 1;
                        while (true) {
                            String framePath = outputDirectory + File.separator + String.format("frame%03d.jpg", i);
                            if (new File(framePath).exists()) {
                                framePaths.add(framePath);
                                i++;
                            } else {
                                break;
                            }
                        }


                        // Aplica el efecto a cada fotograma y guarda los resultados en una lista (parcialmente funciona, pero da error)
                        List<Bitmap> processedFrames = new ArrayList<>();
                        for (String framePath : framePaths) {
                            Bitmap frameBitmap = BitmapFactory.decodeFile(framePath);
                            if (frameBitmap != null) {
                                Bitmap processedFrame = applyHatchPattern(frameBitmap, 9.0f, separador);
                                processedFrames.add(processedFrame);
                            }
                        }

                        // Crea un nuevo video a partir de los fotogramas procesados (parcialmente funciona, pero da error)
                        createVideoFromFrames(outputPath, processedFrames, originalFps, originalWidth, originalHeight);

                    
                        for (String framePath : framePaths) {
                            new File(framePath).delete();
                        }

                       
                        videoView.setVideoURI(Uri.parse(outputPath));
                        videoView.start();

                        Toast.makeText(this, "Video procesado y listo para guardar", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Error al extraer fotogramas del video", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Selecciona un video para aplicar el efecto", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Error al procesar el video", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void createVideoFromFrames(String outputPath, List<Bitmap> frames, String originalFps, String originalWidth, String originalHeight) {
      
        String tempFrameDir = getCacheDir() + File.separator + "temp_frames";
        File tempFrameDirFile = new File(tempFrameDir);
        tempFrameDirFile.mkdirs();

       
        for (int i = 0; i < frames.size(); i++) {
            File frameFile = new File(tempFrameDir, "frame" + i + ".jpg");
            saveBitmap(frames.get(i), frameFile);
        }

        
        String[] cmd = new String[]{
                "-framerate", originalFps,
                "-i", tempFrameDir + File.separator + "frame%d.jpg",
                "-s", originalWidth + "x" + originalHeight,
                "-c:v", "libx264", // Códec de video (ajústalo según tus necesidades)
                outputPath
        };

        int result = FFmpeg.execute(cmd);

        
        for (File frame : tempFrameDirFile.listFiles()) {
            frame.delete();
        }
        tempFrameDirFile.delete();

        if (result == Config.RETURN_CODE_SUCCESS) {
            
            Toast.makeText(this, "Video creado con éxito", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Error al crear el video", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveBitmap(Bitmap bitmap, File file) {
        try {
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveVideo() {
        if (mediaUri != null) {
            String mediaType = getMediaType(mediaUri);
            if (mediaType.equals("video")) {
                String outputDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
                String outputFileName = "processed_video.mp4";
                String outputPath = outputDirectory + File.separator + outputFileName;

                
                extractVideoFramesAndApplyEffect();

               
                File processedVideoFile = new File(outputPath);
                if (processedVideoFile.exists()) {
                   
                    try {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Video.Media.DISPLAY_NAME, outputFileName);
                        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM);

                        ContentResolver contentResolver = getContentResolver();
                        Uri videoUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

                        if (videoUri != null) {
                            try (OutputStream outputStream = contentResolver.openOutputStream(videoUri);
                                 FileInputStream inputStream = new FileInputStream(processedVideoFile)) {
                                byte[] buffer = new byte[1024];
                                int bytesRead;
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }
                                inputStream.close();
                                outputStream.close();
                                Toast.makeText(this, "Video guardado en la galería", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "No se pudo guardar el video", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("MainActivity", "Error al guardar el video: " + e.getMessage());
                        Toast.makeText(this, "Error al guardar el video", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "No se ha podido procesar el video aún. Intenta nuevamente.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Selecciona un video para guardar", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Carga un video antes de guardarlo", Toast.LENGTH_SHORT).show();
        }
    }


    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event); 

        if (scaleFactor > 1.0f) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = x;
                    lastTouchY = y;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = (float) ((x - lastTouchX) * 0.8); 
                    float dy = (float) ((y - lastTouchY) * 0.8); 

                  
                    posX += dx;
                    posY += dy;

                   
                    float maxPosX = imageView.getWidth() * (scaleFactor - 1) / (2 * scaleFactor);
                    float minPosX = -maxPosX;
                    float maxPosY = imageView.getHeight() * (scaleFactor - 1) / (2 * scaleFactor);
                    float minPosY = -maxPosY;

                    posX = Math.max(minPosX, Math.min(posX, maxPosX));
                    posY = Math.max(minPosY, Math.min(posY, maxPosY));

                    imageView.setTranslationX(posX);
                    imageView.setTranslationY(posY);

                    lastTouchX = x;
                    lastTouchY = y;
                    break;
            }
        }

        return true;
    }


    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f));
            imageView.setScaleX(scaleFactor);
            imageView.setScaleY(scaleFactor);
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
           
            scaleFactor = 1.0f;
            imageView.setScaleX(scaleFactor);
            imageView.setScaleY(scaleFactor);

         
            posX = 0;
            posY = 0;
            imageView.setTranslationX(posX);
            imageView.setTranslationY(posY);

            return true;
        }
    }
}
