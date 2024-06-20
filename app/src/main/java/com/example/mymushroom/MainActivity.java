package com.example.mymushroom;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.Manifest;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.mymushroom.ml.Model;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.util.Arrays;
import java.util.Comparator;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST = 1888;
    private static final int MY_CAMERA_OREMISSION_CODE = 100;

    //----------------------------เรียกใช้ฟังชั่น android:id----------------------------------//
    TextView result, confidence, comment;
    Button Camerabutton, selectBtn;
    ImageView imageView;
    Bitmap bitmap;
    //----------------------------เรียกใช้ฟังชั่น android:id----------------------------------//

    int imageSize = 224;     //ขนาดรูปภาพ


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //-----------------------------------นำฟังชั่นมาใส่ในตัวแปลแต่ละตัวเพื่อใช้งาน------------------------------------//
        result = findViewById(R.id.result);
        comment = findViewById(R.id.comment);
        confidence = findViewById(R.id.confidence);
        imageView = findViewById(R.id.imageView);
        selectBtn = findViewById(R.id.selectBtn);
        Camerabutton = findViewById(R.id.button);
        //-----------------------------------นำฟังชั่นมาใส่ในตัวแปลแต่ละตัวเพื่อใช้งาน------------------------------------//

        //-----------------------------------ปุ่มนำเข้าจาก Select image------------------------------------//
        selectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent,10);
            }
        });
        //-----------------------------------ปุ่มนำเข้าจาก Select image ------------------------------------//


        //-----------------------------------ปุ่มเปิดกล้อง------------------------------------//
        Camerabutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent,12);
            }
        });
        //-----------------------------------ปุ่มเปิดกล้อง-----------------------------------//

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //-------------------------------รูปภาพถ่าย---------------------------------//
        if (requestCode == 10) {
            if (data != null) {
                Uri uri = data.getData();
                try {
                    try {
                        bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri); // Set ให้รูปมาอยู่ในตัวแปร bitmap //
                        imageView.setImageBitmap(bitmap); // set ให้รูปจาก bitmap มาอยู่ใน imageView //
                        //ImageView imageView = (ImageView) findViewById(R.id.imageView);
                        //imageView.setImageBitmap(bitmap);

                        bitmap = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, false);
                        classifyImage(bitmap);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                } catch (RuntimeException e) {
                    throw new RuntimeException(e);
                }
                //-------------------------------รูปภาพถ่าย----------------------------------//
            }
            //-------------------------------google dive---------------------------------//
        }else if(requestCode == 12){
            Bundle extras = data.getExtras();
            Bitmap bitmap;
            bitmap = (Bitmap) extras.get("data");
            ImageView imageView = (ImageView) findViewById(R.id.imageView);
            imageView.setImageBitmap(bitmap);

            bitmap = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, false);
            classifyImage(bitmap);
        }
        //-------------------------------google dive---------------------------------//S

    }
    private Bitmap preprocessBitmap(Bitmap bitmap) {
        // ตัวอย่างการปรับแสงสว่างและคอนทราสต์
        bitmap = adjustBrightness(bitmap, 1.2f);  // ปรับแสงสว่าง
        bitmap = adjustContrast(bitmap, 1.1f);    // ปรับคอนทราสต์
        return bitmap;
    }

    private Bitmap adjustBrightness(Bitmap bitmap, float factor) {
        // ปรับแสงสว่าง
        Bitmap adjustedBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        for (int x = 0; x < bitmap.getWidth(); x++) {
            for (int y = 0; y < bitmap.getHeight(); y++) {
                int pixel = bitmap.getPixel(x, y);
                int r = Math.min(255, Math.max(0, (int) (((pixel >> 16) & 0xFF) * factor)));
                int g = Math.min(255, Math.max(0, (int) (((pixel >> 8) & 0xFF) * factor)));
                int b = Math.min(255, Math.max(0, (int) ((pixel & 0xFF) * factor)));
                adjustedBitmap.setPixel(x, y, Color.rgb(r, g, b));
            }
        }
        return adjustedBitmap;
    }

    private Bitmap adjustContrast(Bitmap bitmap, float factor) {
        // ปรับคอนทราสต์
        Bitmap adjustedBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        for (int x = 0; x < bitmap.getWidth(); x++) {
            for (int y = 0; y < bitmap.getHeight(); y++) {
                int pixel = bitmap.getPixel(x, y);
                float[] hsv = new float[3];
                Color.RGBToHSV(Color.red(pixel), Color.green(pixel), Color.blue(pixel), hsv);
                hsv[2] *= factor;
                int newPixel = Color.HSVToColor(Color.alpha(pixel), hsv);
                adjustedBitmap.setPixel(x, y, newPixel);
            }
        }
        return adjustedBitmap;
    }

    public void classifyImage(Bitmap bitmap){
        try {

            Model model = Model.newInstance(getApplicationContext());

            // Creates inputs for reference.
            //---------------------------------รับรูปภาพและปรับขนาด------------------------------//
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            int [] intValues = new int[imageSize * imageSize];
            bitmap.getPixels(intValues,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());


            int pixel = 0;
            for (int i = 0; i < imageSize; i++){
                for (int j = 0; j < imageSize; j++){
                    int val = intValues[pixel++];
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat(((val >> 8)& 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat(((val & 0xFF) * (1.f / 255.f)));
                }
            }

            inputFeature0.loadBuffer(byteBuffer); // นำค่า Pixel ที่ได้มาไว้ในตัวแปร inputFeature0 //
            //---------------------------------รับรูปภาพและปรับขนาด------------------------------//

            // Runs model inference and gets result.
            //---------------------------------นำภาพที่ปรับขนาดแล้วมาเปรียบเทียบ------------------------------//
            Model.Outputs outputs = model.process(inputFeature0); // นำ inputFeature0 มาให้ Modell ทำการ Process จากนั้นนำค่าที่ Model Process แล้วมาฝากไว้ในตัวแปร Output //
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            float[] confidences = outputFeature0.getFloatArray();       // ทำการประกาศตัวแปร confidences ให้เป็น float โดยให้มีค่าเป็น outputFeature0
            int maxPos = 0;                                             // ประกาศ MaxPos ให้เป็น 0 //
            float maxConfidence = 0;                                    // ประกาศ maxConfidence ให้เป็น 0 //
            for (int i = 0; i < confidences.length; i++){               // กำหนดลูป โดยถ้า i = 0 i มีค่าน้อยกว่า จำนวนตำแหน่งของ confidences ให้ i+1
                if (confidences[i] > maxConfidence){                    // ถ้า confidences ตำแหน่งที่ i มากกว่าค่าของ maxConfidence
                    maxConfidence = confidences[i];                     // Set ให้ maxConfidence มีค่าเท่ากับตำแหน่งของ confidences
                    maxPos = i;                                         // Set ให้ maxPos มีค่าเท่ากับ i //
                }
            }
            String[] classes = {"เห็ดกระด้าง","เห็ดขมิ้น","เห็ดแครง","เห็ดจมูกหมู","เห็ดจาวมะพร้าว","เห็ดตับเต่า","เห็ดถ้วยแชมเปญ","เห็ดหล่ม","เห็ดระโงก","เห็ดพิษ","รูปภาพต่างๆ","เห็ดในตลาด"};
            result.setText(classes[maxPos]);
            comment.setText("");

            String ss = ""; //ตัวแปรที่เก็บผลลัพท์ที่ยังไม่หาเปอร์เซ็นและเรียงลำดับและใส่สี
            for (int i = 0; i < classes.length; i++){  //i เริ่มต้นที่ 0 ถ้า i น้อยกว่าจำนวนของ classes ให้ i บวกเพิ่มอีก 1

                ss += String.format("%s: %.1f%%\n",classes[i], confidences[i] * 100); //กำหนด class ของชื่อ และ ค่าเปอร์เซ็นและนำไปเก็บไว้ในตัวแปรที่ชื่อว่า ss

            }

            Map<String, Double> shoesMap = new HashMap<>(); //ประกาศใช้ Map

            // แยกข้อมูลและเก็บใน Map
            double persen = 10.0;     //กำหนดค่าเปอร์เซ็นที่มากกว่าต้องการโชว์
            String sum = "";     //รูปภาพต่างๆ: 50%
                                // jasi : 40%

                                     //ตัวแปรที่เก็บผลลัพท์ที่ยังไม่เรียงลำดับและใส่สี
            String[] lines = ss.split("\n");      //นำผลลัพท์ในตัวแปร ss มาใส่ lines
            for (String line : lines) {
                String[] parts = line.split(":");   //แยกชื่อกับเปอร์เซ็นออกจากกัน
                String shoeName = parts[0].trim();        //เอาแค่ชื่อ
                double percentage = Double.parseDouble(parts[1].replace("%", "").trim());  //เอาค่าเปอร์เซ็นมาตัดเอาเปอร์เซ็นออกโดยแปลงจาก string เป็น double
                if(percentage >= persen){                 //หาว่าค่าไหนมากกว่าเปอร์เซ็นที่ตั้งไว้ที่ตัวแปร persen
                    sum += String.format("%s: %.1f%%\n",shoeName,percentage);               //กำหนด class ของชื่อ และ ค่าเปอร์เซ็นและนำไปเก็บไว้ในตัวแปรที่ชื่อว่า sum
                }
            }
            String[] shoeArray = sum.split("\n");    //นำผลลัพท์ในตัวแปร sum มาใส่ shoeArray

            // เรียงลำดับ String ตามเปอร์เซ็นต์จากมากไปหาน้อย
            Arrays.sort(shoeArray, Comparator.comparingDouble(s -> {
                String[] parts = s.split(": ");
                if (parts.length > 1) {
                    String percentageString = parts[1].replace("%", "");
                    return -Double.parseDouble(percentageString); // ใส่เครื่องหมายลบเพื่อเรียงจากมากไปหาน้อย
                }
                return 0.0;
            }));



            SpannableStringBuilder stringBuilder = new SpannableStringBuilder();    // สร้าง StringBuilder เพื่อเก็บผลลัพธ์


            for (int i = 0; i < shoeArray.length; i++) {                            // เพิ่ม String ที่เรียงลำดับเข้าสู่ StringBuilder
                String line = shoeArray[i];
                stringBuilder.append(line);


                if (i == 0) {
                    stringBuilder.setSpan(new ForegroundColorSpan(Color.GREEN), 0, line.length(), 0);           // ใส่สีตัวอักษรแรกของบรรทัดแรก
                }


                stringBuilder.append("\n");                     // เพิ่มตัวละครว่างระหว่างบรรทัด (ย่อหน้า)
            }

            confidence.setText(stringBuilder);                  // แสดงผลลัพท์
            model.close();
        } catch (IOException e) {
            // TODO Handle the exception
        }

        //---------------------------------นำภาพที่ปรับขนาดแล้วมาเปรียบเทียบ------------------------------//
    }
}