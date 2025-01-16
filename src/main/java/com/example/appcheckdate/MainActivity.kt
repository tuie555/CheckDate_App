package com.example.appcheckdate

import android.content.Context
import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraControl
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.appcheckdate.ui.theme.AppcheckdateTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning

class MainActivity : ComponentActivity() {
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Start the camera if permission is granted
                setContent {
                    AppcheckdateTheme {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            CameraPreview(modifier = Modifier.padding(innerPadding))
                        }
                    }
                }
            } else {
                Log.e("CameraPermission", "Camera permission denied")
            }
        }

        // Request camera permission
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
}

@Composable
fun CameraPreview( modifier: Modifier = Modifier) {
    // Use AndroidView to create a PreviewView
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                // You can set up any additional properties for the PreviewView here
            }
        },
        modifier = modifier
    ) { previewView ->
        startCamera(previewView, previewView.context)
    }
}

private fun startCamera(previewView: PreviewView, context: Context) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

        val preview = androidx.camera.core.Preview.Builder().build()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        preview.setSurfaceProvider(previewView.surfaceProvider)

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        Log.d("CameraSetup", "Setting up image analysis")
        val camera = cameraProvider.bindToLifecycle(context as LifecycleOwner, cameraSelector, preview, imageAnalysis)
        val cameraControl = camera.cameraControl
        val cameraInfo = camera.cameraInfo
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), ImageAnalyzer(cameraControl))

        Log.d("CameraSetup", "Camera successfully bound to lifecycle")
    }, ContextCompat.getMainExecutor(context))
}



private class ImageAnalyzer(private val cameraControl: CameraControl) : ImageAnalysis.Analyzer {

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        Log.d("ImageAnalyzer", "Analyzing image...")
        Log.d("ImageAnalyzer", "ImageProxy: width=${imageProxy.width}, height=${imageProxy.height}, format=${imageProxy.format}")
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            Log.d("ImageAnalyzer", "Image analyzed")

            // Barcode scanning logic integrated here
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_PDF417,
                    Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_CODE_93,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODABAR,
                    Barcode.FORMAT_ITF,
                )
                .enableAllPotentialBarcodes() // Optional
                .build()

            val scanner = BarcodeScanning.getClient(options)
            scanner.process(inputImage).addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    cameraControl.setZoomRatio(2.0f)
                    for (barcode in barcodes) {
                        val displayValue = barcode.displayValue
                        // Handle the detected barcode
                        Log.d("Barcode", "Detected barcode: $displayValue")

                    }
                } else {
                    cameraControl.setZoomRatio(1.0f)
                }
            }
                .addOnFailureListener { e ->
                    Log.e("Barcode", "Barcode scanning failed", e)
                }

            // Close the imageProxy after processing
            imageProxy.close()
        } else {
            Log.e("ImageAnalyzer", "Media image is null")
            imageProxy.close()
        }
    }
}
