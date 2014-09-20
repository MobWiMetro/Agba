package com.gemuvn.gba;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

//import com.mana.utils.NotificationHelper;

@SuppressLint("DefaultLocale")
public class UpdateActivity extends Activity {
	public Context context;
	public String abs;
	//NotificationHelper mNotificationHelper;
	private long length;
	ProgressDialog dlDialog;
	Handler mHandler = new Handler();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		 System.out.println("URL APP----------:" + getIntent().getExtras().getString("urlApp"));
		installApp(getIntent().getExtras().getString("urlApp"));
	}

	private void showMessage(String title, String message) {
		String button1String = "OK";
		AlertDialog.Builder ad = new AlertDialog.Builder(this);
		ad.setTitle(title);
		ad.setMessage(message);
		ad.setPositiveButton(button1String,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int arg1) {
						dialog.dismiss();
					}
				});
		ad.show();
	}

	private String getDownloadPath() throws IOException {
		File sdcard = Environment.getExternalStorageDirectory();

		String dbfile = sdcard.getAbsolutePath() + File.separator
				+ "gemuvn" + File.separator + "downloads";
		File f = new File(dbfile);
		if (!f.exists()) {
			String state = Environment.getExternalStorageState();
			if (Environment.MEDIA_MOUNTED.equals(state)) {
			} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
				showMessage("Error", "Your sdcard cannot write data. Please check again.");
				throw new IOException();
			} else {
				showMessage(
						"Error",
						"You need a sdcard for update this app");
				throw new IOException();
			}

			f.mkdir();
		}
		return dbfile + File.separator;
	}
	
	private static final long K = 1024;
	private static final long M = K * K;
	private static final long G = M * K;
	private static final long T = G * K;

	public static String convertToStringRepresentation(final long value){
	    final long[] dividers = new long[] { T, G, M, K, 1 };
	    final String[] units = new String[] { "TB", "GB", "MB", "KB", "B" };
	    if(value < 1)
	        throw new IllegalArgumentException("Invalid file size: " + value);
	    String result = null;
	    for(int i = 0; i < dividers.length; i++){
	        final long divider = dividers[i];
	        if(value >= divider){
	            result = format(value, divider, units[i]);
	            break;
	        }
	    }
	    return result;
	}

	private static String format(final long value,
	    final long divider,
	    final String unit){
	    final double result =
	        divider > 1 ? (double) value / (double) divider : (double) value;
	    return new DecimalFormat("#,##0.#").format(result) + " " + unit;
	}

	public void installApp(final String inet) {
		dlDialog = new ProgressDialog(UpdateActivity.this);
		dlDialog.setOnKeyListener(new OnKeyListener() {

			@Override
			public boolean onKey(DialogInterface arg0, int arg1, KeyEvent arg2) {
				// TODO Auto-generated method stub
				return true;
			}
		});
		dlDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		dlDialog.setTitle("Downloading");
		dlDialog.setMessage("Connecting");
		dlDialog.setOnCancelListener(new OnCancelListener() {
			
			@Override
			public void onCancel(DialogInterface dialog) {
				// TODO Auto-generated method stub
				UpdateActivity.this.finish();
			}
		});
		
		dlDialog.show();
		

		new Thread(new Runnable() {

			@Override
			public void run() {
				InputStream is = null;
				OutputStream os = null;
				URLConnection URLConn = null;

				try {
					URL fileUrl;
					byte[] buf;
					int ByteRead = 0;
					int ByteWritten = 0;
					fileUrl = new URL(inet);

					URLConn = fileUrl.openConnection();
					is = URLConn.getInputStream();
					
					try {
						List<String> values = URLConn.getHeaderFields().get("content-Length");
						length = Long.parseLong(values.get(0));
					} catch (Exception e) {
						// TODO: handle exception
					}
					final String sLength = convertToStringRepresentation(length);
					String fileName = inet.substring(inet.lastIndexOf("/") + 1);

					String filePath = null;
					try {
						filePath = getDownloadPath();
					} catch (IOException e) {
						e.printStackTrace();
						return;
					}

					File f = new File(filePath);
					f.mkdirs();
					final String abs = filePath + fileName;
					f = new File(abs);

					os = new BufferedOutputStream(new FileOutputStream(abs));
					buf = new byte[1024];

					/*
					 * This loop reads the bytes and updates a progressdialog
					 */
					while ((ByteRead = is.read(buf)) != -1) {

						os.write(buf, 0, ByteRead);
						ByteWritten += ByteRead;

						final int tmpWritten = ByteWritten;
						runOnUiThread(new Runnable() {

							public void run() {
								if(length>0) {
									String percen = String.valueOf((int)(tmpWritten*100/length));
									dlDialog.setMessage("Total size: "+sLength+" - " + percen + "%");
								} else {
									dlDialog.setMessage(""+tmpWritten+ " %");
								}
							}

						});
					}
					
					runOnUiThread(new Runnable() {

						public void run() {
							dlDialog.setMessage("Complete");
						}

					});
					
					mHandler.postDelayed(new Runnable() {
						
						@Override
						public void run() {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									// dlDialog.setTitle("Start");
									try {
										Intent intent = new Intent(Intent.ACTION_VIEW);
										intent.setDataAndType(Uri.fromFile(new File(abs)), "application/vnd.android.package-archive");
										UpdateActivity.this.startActivity(intent);
									} catch (Exception e) {
										// TODO: handle exception
										Toast.makeText(UpdateActivity.this, "Saved to /SDCard/n64emu", Toast.LENGTH_LONG).show();
									}
									UpdateActivity.this.finish();
									dlDialog.dismiss();
								}
							});
						}
					}, 1000);

					is.close();
					os.flush();
					os.close();
					Thread.sleep(200);
					
					// Intent intent = new Intent(Intent.ACTION_VIEW);
					// intent.setDataAndType(Uri.fromFile(new File(abs)),
					// "application/vnd.android.package-archive");
					// startActivity(intent);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		// TODO Auto-generated method stub
		super.onWindowFocusChanged(hasFocus);
		Log.d("hasFocus", String.valueOf(hasFocus));
		//MainActivity.exitHandeler(this, hasFocus);
	}
	
	@Override
	public void onBackPressed() {
		return;
	}
}