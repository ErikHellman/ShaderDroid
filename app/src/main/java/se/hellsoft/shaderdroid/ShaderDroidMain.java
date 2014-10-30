package se.hellsoft.shaderdroid;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 *
 */
public class ShaderDroidMain extends Activity implements View.OnTouchListener, Visualizer.OnDataCaptureListener {
	public static final String TAG = "ShaderDroid";
	private GLSurfaceView mGLSurfaceView;
	private ShaderRenderer mRenderer;
	private MediaPlayer mMediaPlayer;
	private int mAudioSession;
	private Visualizer mVisualizer;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bitmap visualizerBitmap = startMusic();
		mGLSurfaceView = new GLSurfaceView(this);
		mGLSurfaceView.setEGLContextClientVersion(3);
		mGLSurfaceView.setEGLConfigChooser(false);
		mGLSurfaceView.setDebugFlags(GLSurfaceView.DEBUG_CHECK_GL_ERROR | GLSurfaceView.DEBUG_LOG_GL_CALLS);
		mRenderer = new ShaderRenderer(visualizerBitmap);
		mGLSurfaceView.setRenderer(mRenderer);
		mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
		mGLSurfaceView.setKeepScreenOn(true);
		setContentView(mGLSurfaceView);

		mGLSurfaceView.setOnTouchListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mGLSurfaceView.onResume();
	}

	@Override
	protected void onPause() {
		mGLSurfaceView.onPause();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		stopMusic();
		super.onDestroy();
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if(event.getAction() == MotionEvent.ACTION_MOVE) {
			mRenderer.mMouse[0] = event.getX();
			mRenderer.mMouse[1] = event.getY();
		}
		return true;
	}

	private Bitmap startMusic() {
		mMediaPlayer = MediaPlayer.create(this, R.raw.hyperfun);
		mMediaPlayer.setLooping(true);
		mAudioSession = mMediaPlayer.getAudioSessionId();
		mMediaPlayer.start();
		mVisualizer = new Visualizer(mAudioSession);
		mVisualizer.setDataCaptureListener(this, Visualizer.getMaxCaptureRate() / 2, true, true);
		mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1] / 2);
		Bitmap visualizerBitmap = Bitmap.createBitmap(Visualizer.getCaptureSizeRange()[1] / 2, 2, Bitmap.Config.RGB_565)
				.copy(Bitmap.Config.RGB_565, true);
		visualizerBitmap.eraseColor(Color.BLACK);
		mVisualizer.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED);
		mVisualizer.setEnabled(true);
		return visualizerBitmap;
	}

	private void stopMusic() {
		mMediaPlayer.release();
		mVisualizer.release();
	}

	@Override
	public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
		mRenderer.setSamplingRate(samplingRate);
		mRenderer.setWaveform(waveform);
	}

	@Override
	public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
		mRenderer.setSamplingRate(samplingRate);
		mRenderer.setFft(fft);
	}

	private class ShaderRenderer implements GLSurfaceView.Renderer {
		private static final int FLOAT_SIZE_BYTES = Float.SIZE / Byte.SIZE;
		private FloatBuffer mRectData;
		private String mVertexShader;
		private String mFragmentShader;
		private float[] mResolution;
		private int miResolutionHandle;
		private int miGlobalTimeHandle;
		private int miMouseHandle;
		private int miChannel0;
		private float[] mMouse = new float[] {0,0,0,0};
		private int maPositionHandle;
		private long mStartTime;
		private int mSamplingRate;
		private int[] mWaveform;
		private int[] mFft;
		public Bitmap mVisualizerBitmap;

		private ShaderRenderer(Bitmap visualizerBitmap) {
			mVisualizerBitmap = visualizerBitmap;
			float[] rectData = new float[]{
					-1f, -1f,
					-1f, 1f,
					1f, -1f,
					1f, 1f,
			};

			mRectData = ByteBuffer.allocateDirect(rectData.length * FLOAT_SIZE_BYTES)
					.order(ByteOrder.nativeOrder()).asFloatBuffer();
			mRectData.put(rectData).position(0);

			mVertexShader = readShader(R.raw.vertex_shader, ShaderDroidMain.this);
			mFragmentShader = readShader(R.raw.input_sound, ShaderDroidMain.this);
		}

		@Override
		public void onSurfaceCreated(GL10 gl, EGLConfig config) {
			int program = createProgram(mVertexShader, mFragmentShader);
			if (program == 0) {
				Log.e(TAG, "createProgram failed!");
				return;
			}
			maPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
			checkGlError("glGetAttribLocation aPosition");
			if (maPositionHandle == -1) {
				throw new RuntimeException("Could not get attrib location for aPosition");
			}

			miGlobalTimeHandle = GLES20.glGetUniformLocation(program, "iGlobalTime");
			checkGlError("glGetUniformLocation iGlobalTime");
			if (miGlobalTimeHandle == -1) {
				throw new RuntimeException("Could not get attrib location for miGlobalTimeHandle");
			}

			miResolutionHandle = GLES20.glGetUniformLocation(program, "iResolution");
			checkGlError("glGetUniformLocation iResolution");
			if (miResolutionHandle == -1) {
				throw new RuntimeException("Could not get attrib location for miResolutionHandle");
			}

			miMouseHandle = GLES20.glGetUniformLocation(program, "iMouse");
			checkGlError("glGetUniformLocation iMouse");
			if (miMouseHandle == -1) {
				throw new RuntimeException("Could not get attrib location for miMouseHandle");
			}

			int[] textures = new int[1];
			GLES20.glGenTextures(1, textures, 0);

			miChannel0 = textures[0];
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, miChannel0);

			GLES20.glUseProgram(program);
			checkGlError("glUseProgram mProgram");
			GLES20.glEnableVertexAttribArray(maPositionHandle);
			checkGlError("glEnableVertexAttribArray maPositionHandle");
			mRectData.position(0);
			GLES20.glVertexAttribPointer(maPositionHandle, 2, GLES20.GL_FLOAT, false, 0, mRectData);
			checkGlError("glVertexAttribPointer maPosition");

			mStartTime = SystemClock.elapsedRealtime();
		}

		@Override
		public void onSurfaceChanged(GL10 gl, int width, int height) {
			mResolution = new float[] {width, height, 1f};
			GLES20.glViewport(0, 0, width, height);
			checkGlError("glViewPort");
		}

		@Override
		public void onDrawFrame(GL10 gl) {
			GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
			checkGlError("glClear");
			GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
			checkGlError("glClearColor");

			GLES20.glUniform4fv(miMouseHandle, 1, mMouse, 0);
			checkGlError("glUniform4f");
			GLES20.glUniform3fv(miResolutionHandle, 1, mResolution, 0);
			checkGlError("glUniform3f");
			long nowInSec = SystemClock.elapsedRealtime();
			GLES20.glUniform1f(miGlobalTimeHandle, ((float) (nowInSec - mStartTime)) / 1000f);
			checkGlError("glUniform1f");

			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			checkGlError("glActiveTexture");
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, miChannel0);
			checkGlError("glBindTexture");
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
			checkGlError("glTexParameteri");
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
			checkGlError("glTexParameteri");

			// Load the bitmap into the bound texture.
			GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mVisualizerBitmap, 0);
			checkGlError("texImage2D");

			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
			checkGlError("glDrawArrays");
		}

		private int loadShader(int shaderType, String source) {
			int shader = GLES20.glCreateShader(shaderType);
			if (shader != 0) {
				GLES20.glShaderSource(shader, source);
				GLES20.glCompileShader(shader);
				int[] compiled = new int[1];
				GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
				if (compiled[0] == 0) {
					Log.e(TAG, "Could not compile shader " + shaderType + ":");
					Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
					GLES20.glDeleteShader(shader);
					shader = 0;
				}
			}
			return shader;
		}

		private int createProgram(String vertexSource, String fragmentSource) {
			int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
			if (vertexShader == 0) {
				return 0;
			}

			int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
			if (pixelShader == 0) {
				return 0;
			}

			int program = GLES20.glCreateProgram();
			if (program != 0) {
				GLES20.glAttachShader(program, vertexShader);
				checkGlError("glAttachShader");
				GLES20.glAttachShader(program, pixelShader);
				checkGlError("glAttachShader");
				GLES20.glLinkProgram(program);
				int[] linkStatus = new int[1];
				GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
				if (linkStatus[0] != GLES20.GL_TRUE) {
					Log.e(TAG, "Could not link program: ");
					Log.e(TAG, GLES20.glGetProgramInfoLog(program));
					GLES20.glDeleteProgram(program);
					program = 0;
				}
			}
			return program;
		}

		private String readShader(int resource, Context context) {
			Resources resources = context.getResources();
			InputStream inputStream = resources.openRawResource(resource);
			InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			char[] buffer = new char[32];
			int charsRead;
			StringBuilder stringBuilder = new StringBuilder();

			try {
				while ((charsRead = bufferedReader.read(buffer)) != -1) {
					stringBuilder.append(buffer, 0, charsRead);
				}
			} catch (IOException e) {
				Log.e(TAG, "Error reading resource " + resource, e);
			}

			return stringBuilder.toString();
		}

		private void checkGlError(String op) {
			int error;
			boolean hasError = false;
			while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
				Log.e(TAG, op + ": glError " + error);
				hasError = true;
			}
			if (hasError) {
				throw new RuntimeException(op + ": glError " + error);
			}
		}

		public void setSamplingRate(int samplingRate) {
			mSamplingRate = samplingRate;
		}

		public void setWaveform(byte[] waveform) {
			Log.d(TAG, "Waveform: " + Arrays.toString(waveform));
			if (mWaveform == null || mWaveform.length != waveform.length) {
				mWaveform = new int[waveform.length];
			}
			for (int i = 0; i < waveform.length; i++) {
				mWaveform[i] = waveform[i];
			}
			mVisualizerBitmap.setPixels(mWaveform, 0, mWaveform.length, 0, 1, mWaveform.length, 1);
		}

		public void setFft(byte[] fft) {
			if(mFft == null || mFft.length != fft.length) {
				mFft = new int[fft.length];
			}
			for (int i = 0; i < fft.length; i++) {
				mFft[i] = fft[i];
			}
			mVisualizerBitmap.setPixels(mFft, 0, mFft.length, 0, 0, mFft.length, 1);
		}

	}
}
