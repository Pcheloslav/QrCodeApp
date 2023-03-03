package com.example.qrcodescanner

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*


class MainActivity : AppCompatActivity() {
    var imguri: Uri? = null
    private var barcodeScannerOptions: BarcodeScannerOptions? = null
    private var barcodeScanner:BarcodeScanner? = null
    private lateinit var storagePermissions :Array<String>
    private lateinit var cameraPermissions :Array<String>
    var fileOut:FileOutputStream? = null
    private val STORAGE_REQUEST_CODE = 100
    private val CAMERA_REQUEST_CODE = 101
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        storagePermissions = arrayOf(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        cameraPermissions = arrayOf(android.Manifest.permission.CAMERA)


        barcodeScannerOptions = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
        barcodeScanner = BarcodeScanning.getClient(barcodeScannerOptions!!)

        cameraBtn.setOnClickListener {
            if (checkCameraPermissions() && checkPermission())
            {
                pickImageCamera()
            }
            else
            {
                requestCameraPermissions()
            }
        }

        generate.setOnClickListener {
            val text = resultText.text.toString().trim()
            if(text.trim().isEmpty()){

                Toast.makeText(this,"Empty",Toast.LENGTH_SHORT).show()

            }else{

                val bitmap = generateQRCode(text.trim())
                saveImageToExternalStorage(this, bitmap)
                img.setImageBitmap(bitmap)
                //imguri = getimguri(this, bitmap)
            }
        }
        browser.setOnClickListener {
            val text = resultText.text.toString().trim()
            if (!text.isEmpty()) {
                openWebPage(text)
            }
            else
            {
                Toast.makeText(this,"Empty",Toast.LENGTH_SHORT).show()
            }
        }

        choosebtn.setOnClickListener{
            if (checkPermission())
            {
                pickImage()
            }
            else
            {
                requestPermission()
            }
        }

        scanbtn.setOnClickListener {
            if (imguri == null )
            {
                showToast("Pick image!")
            }
            else
            {
                detectResultsFromImage()
            }
        }




    }
    fun getimguri(inContext: Context, inImage: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(
            inContext.contentResolver,
            inImage,
            "Title",
            null
        )
        return Uri.parse(path)
    }
    @SuppressLint("QueryPermissionsNeeded")
    fun openWebPage(url: String) {
        var webpage = Uri.parse(url)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            webpage = Uri.parse("http://$url")
        }
        val intent = Intent(Intent.ACTION_VIEW, webpage)
            //if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        //}
    }


    private fun detectResultsFromImage() {
        Log.d("MAIN_TAG", "detected fron image")
        try {
            val inputImage = InputImage.fromFilePath(this@MainActivity, imguri!!)
            val barcodeResult = barcodeScanner?.process(inputImage)!!
                .addOnSuccessListener {barcodes->
                    extractQrCodeInfo(barcodes)


            }
                .addOnFailureListener{e->
                    Log.d("MAIN_TAG", "detected fron image", e)
                    showToast("Failed ${e.message}")
                }

        }
        catch (e:Exception)
        {
            Log.d("MAIN_TAG", "detected fron image", e)
            showToast("Failed ${e.message}")
        }
    }

    private fun extractQrCodeInfo(barcodes: List<Barcode>) {
            for (barcode in barcodes) {
                val bound = barcode.boundingBox
                val corners = barcode.cornerPoints
                val rawvalue = barcode.rawBytes
                val valueType = barcode.valueType
                when (valueType) {
                    Barcode.TYPE_URL -> {
                        val typeUrl = barcode.url
                        val title = "${typeUrl?.title}"
                        val url = "${typeUrl?.url}"
                        resultText.setText(url)
                    }
                    else -> {
                        //showToast("бляяяяяяяяяяя")
                        //resultText.text.clear()
                        resultText.setText(String(rawvalue!!, Charsets.UTF_8))

                    }
                }


            }

    }


    private fun pickImage()
    {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        galleryActivityResultLauncher.launch(intent)
    }
    private fun pickImageCamera()
    {
        val contentValues = ContentValues()
        contentValues.put(MediaStore.Images.Media.DESCRIPTION, "Image_" + ".jpg")
        contentValues.put(MediaStore.Images.Media.TITLE, "image/jpeg")
        imguri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imguri)
        cameraActivityResultLauncher.launch(intent)
    }
    private val cameraActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
    {result->
        if (result.resultCode == Activity.RESULT_OK)
        {
            val data = result.data
            Log.d("MAIN_TAG", "img $imguri")
            //imguri = data?.data
            img.setImageURI(imguri)
        }
        else
        {
            showToast("aaaaaaaaa")
        }

    }
    private val galleryActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
    {result->
        if (result.resultCode == Activity.RESULT_OK)
        {
            val data = result.data
            Log.d("MAIN_TAG", "img $imguri")
            imguri = data?.data
            img.setImageURI(imguri)
        }
        else
        {
            showToast("aaaaaaaaa")
        }

    }
    private fun showToast(message:String)
    {
        Toast.makeText(this, message,Toast.LENGTH_SHORT).show()
    }


    private fun checkCameraPermissions():Boolean
    {
        val res = ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        val resstorrage = checkPermission()
        return res && resstorrage
    }
    private fun requestCameraPermissions()
    {
        ActivityCompat.requestPermissions(this@MainActivity, cameraPermissions, CAMERA_REQUEST_CODE)
    }
    private val storageActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        Log.d(TAG, "storageActivityResultLauncher: ")
        //here we will handle the result of our intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            //Android is 11(R) or above
            if (Environment.isExternalStorageManager()){
                //Manage External Storage Permission is granted
                Log.d(TAG, "storageActivityResultLauncher: Manage External Storage Permission is granted")
            }
            else{
                //Manage External Storage Permission is denied....
                Log.d(TAG, "storageActivityResultLauncher: Manage External Storage Permission is denied....")
                showToast("Manage External Storage Permission is denied....")
            }
        }
        else{
            //Android is below 11(R)
        }
    }
    private fun checkPermission(): Boolean{
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            //Android is 11(R) or above
            Environment.isExternalStorageManager()
        }
        else{
            //Android is below 11(R)
            val write = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val read = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            write == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED
        }
    }
    private fun requestPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            //Android is 11(R) or above
            try {
                Log.d(TAG, "requestPermission: try")
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                val uri = Uri.fromParts("package", this.packageName, null)
                intent.data = uri
                storageActivityResultLauncher.launch(intent)
            }
            catch (e: Exception){
                Log.e(TAG, "requestPermission: ", e)
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                storageActivityResultLauncher.launch(intent)
            }
        }
        else{
            //Android is below 11(R)
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE_REQUEST_CODE
            )
        }
    }


    private fun saveImageToExternalStorage(context: Context, finalBitmap: Bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "Image_" + ".jpg")
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            contentValues.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + "TestFolder"
            )
            val imageUri: Uri? =
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            try {
                val outputStream = resolver.openOutputStream(Objects.requireNonNull(imageUri!!))
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                Objects.requireNonNull(outputStream)
                Toast.makeText(context, "Image Saved", Toast.LENGTH_SHORT).show()
            } catch (e: java.lang.Exception) {
                Toast.makeText(context, "Image Not Not  Saved: \n $e", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }



    }

    private fun generateQRCode(text: String): Bitmap {
        val width = 150
        val height = 150
        val hints: Hashtable<EncodeHintType, String> = Hashtable<EncodeHintType, String>()
        hints.put(EncodeHintType.CHARACTER_SET, "utf-8")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val codeWriter = MultiFormatWriter()
        try {
            val bitMatrix =
                codeWriter.encode(text, BarcodeFormat.QR_CODE, width, height, hints)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val color = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                    bitmap.setPixel(x, y, color)
                }

            }
        } catch (e: WriterException) {

            Log.d(TAG, "generateQRCode: ${e.message}")

        }
        return bitmap
    }



override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode)
        {
            STORAGE_REQUEST_CODE-> {
                if (grantResults.isNotEmpty()) {
                    val storageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    if (storageAccepted) {
                        pickImage()
                    } else {
                        showToast("Storage permissions rrequired")
                    }
                }
            }
            CAMERA_REQUEST_CODE-> {
                if (grantResults.isNotEmpty()) {
                    val storageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    if (storageAccepted) {
                        pickImageCamera()
                    } else {
                        showToast("Camera permissions rrequired")
                    }
                }
            }


        }
    }
}
