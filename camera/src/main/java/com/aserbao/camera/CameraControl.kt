package com.aserbao.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.aserbao.camera.utils.CameraOperaion
import com.aserbao.camera.utils.ImageSaver
import java.io.File
import java.util.*

/*
 * 作用：
 * @author aserbao
 * @date: on 2020/10/19
 * @project: AserbaosAndroid
 * @package: com.aserbao.camera
 */
class CameraControl(var context: Context,var mCamera2View: Camera2View) : ICamera {
    companion object{
        const val IMAGE_WIDTH = 1920
        const val IMAGE_HEIHGT = 1080
    }


    open var cameraDevice:CameraDevice ?= null
    private var captureRequest: CaptureRequest? = null
    var mCameraCharacteristics:CameraCharacteristics ?= null
    private var mediaRecorder: MediaRecorder? = null
    /**
     * The [android.util.Size] of video recording.
     */
    private lateinit var videoSize: Size
    private lateinit var previewSize: Size // 最适合的尺寸
    private var imageReader: ImageReader? = null


    /**
     * This is the output file for our picture.
     */
    private lateinit var picFile: File
    /**
     * This a callback object for the [ImageReader]. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    val onImageAvailableListener =  ImageReader.OnImageAvailableListener {
        Thread(ImageSaver(it.acquireNextImage(), picFile)).start()
    }

    /**
     * A reference to the current [android.hardware.camera2.CameraCaptureSession] for
     * preview.
     */
    private var captureSession: CameraCaptureSession? = null

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    /**
     * [CaptureRequest] generated by [.previewRequestBuilder]
     */
    private lateinit var previewRequest: CaptureRequest


    var mStateCall = object: CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera;
            startPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {}
        override fun onError(camera: CameraDevice, error: Int) {}

        override fun onClosed(camera: CameraDevice) {
            super.onClosed(camera)
            cameraDevice  = null
        }
    }


    init {
        picFile = File(createPicFileName(context,"${System.currentTimeMillis()}.jpg"))
    }

    /**
     * 打开相机权限
     * @param holder
     */
    override fun openCamera(cId: Int, width: Int, height: Int) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "请提供相机权限", Toast.LENGTH_SHORT).show()
            return
        }
        val mCameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var usedCameraId: String? = null
        try {
            if (mCameraManager != null) {
                for (cameraId in mCameraManager.getCameraIdList()) {
                    mCameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId)
                    val facing: Int = mCameraCharacteristics!!.get(CameraCharacteristics.LENS_FACING)!!
                    if (!(facing == null && cId == facing)) {
                        usedCameraId = cameraId
                        setupCameraCharacteristics(mCameraCharacteristics!!,width,height)
                        break
                    }
                }
            }
            mediaRecorder = MediaRecorder()
            mCameraManager.openCamera(usedCameraId!!,mStateCall,null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * 获取适应的尺寸
     * @param characteristics CameraCharacteristics
     * @param width Int
     * @param height Int
     */
    private fun setupCameraCharacteristics(characteristics: CameraCharacteristics,width: Int,height: Int) {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?:
        throw RuntimeException("Cannot get available preview/video sizes")
        videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
        var outputSizes = map.getOutputSizes(SurfaceTexture::class.java)
        previewSize = CameraOperaion.chooseOptimalSize(outputSizes,mCamera2View.width,mCamera2View.height,1920,1080,mCamera2View.mRatioWH)!!

        CameraOperaion.chooseOptimalPicSize(map.getOutputSizes(ImageFormat.JPEG),IMAGE_WIDTH,
            IMAGE_HEIHGT,mCamera2View.mRatioWH)
        imageReader = ImageReader.newInstance(previewSize.width, previewSize.height,
            ImageFormat.JPEG, 2).apply {
            setOnImageAvailableListener(onImageAvailableListener, null)
        }
    }

    /**
     * we don't use sizes larger than 1080p,MediaRecorder cannot handle such a high-resolution video
     * @param choices Array<Size>
     * @return Size
     */
    private fun chooseVideoSize(choices: Array<Size>) = choices.firstOrNull {
        it.width == ((it.height / mCamera2View.mRatioWH).toInt()) && it.width <= 1080 } ?: choices[choices.size - 1]



    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its status.
     */
    private fun startPreview() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            var surfaceTexture = mCamera2View.surfaceTexture
            surfaceTexture.setDefaultBufferSize(previewSize.width,previewSize.height)
            val surface = Surface(surfaceTexture)

            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)
            try {
                cameraDevice?.createCaptureSession(Arrays.asList(surface,imageReader?.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        updatePreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }, null)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    fun updatePreview(){
        try {
            previewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            HandlerThread("CameraPreview").start()
            previewRequest = previewRequestBuilder.build()
            captureSession?.setRepeatingRequest(previewRequest, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber)
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }


    /**
     * 创造对应的图片文件路径
     * @param context
     * @param fileName
     * @return
     */
    fun createPicFileName(context: Context, fileName: String): String? {
        val externalStorageState = Environment.getExternalStorageState()
        return if (externalStorageState == Environment.MEDIA_MOUNTED) {
            val file = File(Environment.getExternalStorageDirectory().absolutePath + "/" + "spot/pic/"
                + fileName)
            if (!file.parentFile.exists()) {
                file.parentFile.mkdirs()
            }
            file.absolutePath
        } else {
            context.filesDir.toString() + "/" + fileName
        }
    }
}