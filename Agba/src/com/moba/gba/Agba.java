package com.moba.gba;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;

import org.json.JSONArray;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.androidemu.Emulator;
import com.androidemu.EmulatorView;
import com.androidemu.gba.input.GameKeyListener;
import com.androidemu.gba.input.Keyboard;
import com.androidemu.gba.input.Trackball;
import com.androidemu.gba.input.VirtualKeypad;
import com.moba.util.IabHelper;
import com.moba.util.IabResult;
import com.moba.util.Inventory;
import com.moba.util.Purchase;
import com.gemuvn.gba.PreferenceHelper;
import com.moba.gba.R;
import com.gemuvn.gba.UpdateActivity;
import com.td.httpclient.HttpClientHelper;

public class Agba extends Activity implements GameKeyListener,
		DialogInterface.OnCancelListener {
	// Debug tag, for logging
	static final String TAG = "Agba";

	// Does the user have the premium upgrade?
	boolean mIsPremium = false;

	// SKUs for our products: the premium upgrade (non-consumable)
	static final String SKU_PREMIUM = "premium";

	// (arbitrary) request code for the purchase flow
	static final int RC_REQUEST = 10001;

	// The helper object
	IabHelper mHelper;

	@SuppressWarnings("unused")
	private static final String LOG_TAG = "GameBoid";

	private static final int REQUEST_BROWSE_ROM = 1;
	private static final int REQUEST_BROWSE_BIOS = 2;
	private static final int REQUEST_SETTINGS = 3;

	private static final int DIALOG_QUIT_GAME = 1;
	private static final int DIALOG_LOAD_STATE = 2;
	private static final int DIALOG_SAVE_STATE = 3;

	private static final int GAMEPAD_LEFT_RIGHT = (Emulator.GAMEPAD_LEFT | Emulator.GAMEPAD_RIGHT);
	private static final int GAMEPAD_UP_DOWN = (Emulator.GAMEPAD_UP | Emulator.GAMEPAD_DOWN);
	private static final int GAMEPAD_DIRECTION = (GAMEPAD_UP_DOWN | GAMEPAD_LEFT_RIGHT);

	private static Emulator emulator;
	private static int resumeRequested;
	private static Thread emuThread;

	private EmulatorView emulatorView;
	private Keyboard keyboard;
	private VirtualKeypad keypad;
	private Trackball trackball;
	private View placeholder;

	private Rect surfaceRegion = new Rect();
	private int surfaceWidth;
	private int surfaceHeight;

	private String currentGame;
	private String lastPickedGame;
	private boolean isMenuShowing;
	private int quickLoadKey;
	private int quickSaveKey;
	private FrameLayout adLeo = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		checkUpdate();

		File datadir = getDir("data", MODE_PRIVATE);
		if (!initEmulator(datadir)) {
			finish();
			return;
		}

		setContentView(R.layout.main);
		emulatorView = (EmulatorView) findViewById(R.id.emulator);
		emulatorView.setEmulator(emulator);
		// switchToView(R.id.empty);
		placeholder = findViewById(R.id.empty);

		// create physical keyboard and trackball
		keyboard = new Keyboard(emulatorView, this);
		trackball = new Trackball(keyboard, this);

		// create virtual keypad
		keypad = (VirtualKeypad) findViewById(R.id.keypad);
		keypad.setGameKeyListener(this);

		// keypad.setVisibility(View.VISIBLE);
		// copy preset files
		copyAsset(new File(datadir, "game_config.txt"));
		copyAsset(new File(datadir, "gba_bios.bin"));

		// load settings
		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		lastPickedGame = settings.getString("lastPickedGame", null);
		loadGlobalSettings();

		// restore state if any
		if (savedInstanceState != null)
			currentGame = savedInstanceState.getString("currentGame");
		// switchToView(currentGame == null ? R.id.empty : R.id.game);

		// load BIOS
		if (loadBIOS(settings.getString("bios", datadir + "/gba_bios.bin"))) {
			// restore last running game
			String last = settings.getString("lastRunningGame", null);
			if (last != null) {
				saveLastRunningGame(null);
				if (new File(getGameStateFile(last, 0)).exists()
						&& loadROM(last, false))
					quickLoad();
			}
		}

		showPlaceholder();
		initIAB();
	}

	private void initIAB() {
		/*
		 * base64EncodedPublicKey should be YOUR APPLICATION'S PUBLIC KEY (that
		 * you got from the Google Play developer console). This is not your
		 * developer public key, it's the *app-specific* public key.
		 * 
		 * Instead of just storing the entire literal string here embedded in
		 * the program, construct the key at runtime from pieces or use bit
		 * manipulation (for example, XOR with some other string) to hide the
		 * actual key. The key itself is not secret information, but we don't
		 * want to make it easy for an attacker to replace the public key with
		 * one of their own and then fake messages from the server.
		 */
		String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA17lRwYCLqj8LTFkOz7yUwsjfQ+zUIMpnOa5B8Z5OfOy6IPTRqoCXq7j6N9exNzGgyBDr3ENQKiMh6LEhtFe53CUQKx7Wzx4AtSLv8dM409/H/TEHxcFHGERdBw/JLv2kJLyRZXS8/LgzEXvK+zc8KtWSXpsa6Nhkv4N6Dsyy3Q7Q5JQ6C0Mne7JW30vLiMHYWlk5rUKqLyTYMoc2J9zp3V+Oo46A9jVjEnSUtqGoWAzkinakJUk2mPhb55ocvAQO5Gq2ybbndIGVTUFRSNwFPYgX0mXKZDMTnI3oWgzpNLvB/H3VTUg08QkKaRdDSOeBkNA7Z+hBaMtzNWEwBtJSxwIDAQAB";

		Log.d(TAG, "Creating IAB helper.");
		mHelper = new IabHelper(this, base64EncodedPublicKey);

		// enable debug logging (for a production application, you should set
		// this to false).
		mHelper.enableDebugLogging(true);

		// Start setup. This is asynchronous and the specified listener
		// will be called once setup completes.
		Log.d(TAG, "Starting setup.");
		mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
			public void onIabSetupFinished(IabResult result) {
				Log.d(TAG, "Setup finished.");

				if (!result.isSuccess()) {
					// Oh noes, there was a problem.
					complain("Problem setting up in-app billing: " + result);
					return;
				}

				// Have we been disposed of in the meantime? If so, quit.
				if (mHelper == null)
					return;

				// IAB is fully set up. Now, let's get an inventory of stuff we
				// own.
				Log.d(TAG, "Setup successful. Querying inventory.");
				mHelper.queryInventoryAsync(mGotInventoryListener);
			}
		});

	}

	// Listener that's called when we finish querying the items and
	// subscriptions we own
	IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
		public void onQueryInventoryFinished(IabResult result,
				Inventory inventory) {
			Log.d(TAG, "Query inventory finished.");

			// Have we been disposed of in the meantime? If so, quit.
			if (mHelper == null)
				return;

			// Is it a failure?
			if (result.isFailure()) {
				complain("Failed to query inventory: " + result);
				return;
			}

			Log.d(TAG, "Query inventory was successful.");

			/*
			 * Check for items we own. Notice that for each purchase, we check
			 * the developer payload to see if it's correct! See
			 * verifyDeveloperPayload().
			 */

			// Do we have the premium upgrade?
			Purchase premiumPurchase = inventory.getPurchase(SKU_PREMIUM);
			mIsPremium = (premiumPurchase != null && verifyDeveloperPayload(premiumPurchase));
			Log.d(TAG, "User is " + (mIsPremium ? "PREMIUM" : "NOT PREMIUM"));

			updateUi();
			setWaitScreen(false);
			Log.d(TAG, "Initial inventory query finished; enabling main UI.");
		}
	};

	// updates UI to reflect model
	public void updateUi() {
		// "Upgrade" button is only visible if the user is not premium
		findViewById(R.id.upgrade).setVisibility(
				mIsPremium ? View.GONE : View.VISIBLE);
	}

	// Enables or disables the "please wait" screen.
	void setWaitScreen(boolean set) {
		findViewById(R.id.empty).setVisibility(set ? View.GONE : View.VISIBLE);
		findViewById(R.id.screen_wait).setVisibility(
				set ? View.VISIBLE : View.GONE);
	}

	void complain(String message) {
		Log.e(TAG, "**** Agba Error: " + message);
		alert("Error: " + message);
	}

	void alert(String message) {
		AlertDialog.Builder bld = new AlertDialog.Builder(this);
		bld.setMessage(message);
		bld.setNeutralButton("OK", null);
		Log.d(TAG, "Showing alert dialog: " + message);
		bld.create().show();
	}

	/** Verifies the developer payload of a purchase. */
	boolean verifyDeveloperPayload(Purchase p) {
		String payload = p.getDeveloperPayload();

		/*
		 * TODO: verify that the developer payload of the purchase is correct.
		 * It will be the same one that you sent when initiating the purchase.
		 * 
		 * WARNING: Locally generating a random string when starting a purchase
		 * and verifying it here might seem like a good approach, but this will
		 * fail in the case where the user purchases an item on one device and
		 * then uses your app on a different device, because on the other device
		 * you will not have access to the random string you originally
		 * generated.
		 * 
		 * So a good developer payload has these characteristics:
		 * 
		 * 1. If two different users purchase an item, the payload is different
		 * between them, so that one user's purchase can't be replayed to
		 * another user.
		 * 
		 * 2. The payload must be such that you can verify it even when the app
		 * wasn't the one who initiated the purchase flow (so that items
		 * purchased by the user on one device work on other devices owned by
		 * the user).
		 * 
		 * Using your own server to store and verify developer payloads across
		 * app installations is recommended.
		 */

		if ("upgrademoba".equals(payload))
			return true;
		return false;
	}

	Handler mHandler = new Handler();

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);

		if (hasFocus) {
			// reset keys
			keyboard.reset();
			if (keypad != null)
				keypad.reset();
			emulator.setKeyStates(0);

			emulator.resume();
		} else
			emulator.pause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (isFinishing()) {
			resumeRequested = 0;
			emulator.cleanUp();
			emulator = null;
		}

		// very important:
		Log.d(TAG, "Destroying helper.");
		if (mHelper != null) {
			mHelper.dispose();
			mHelper = null;
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		pauseEmulator();
	}

	@Override
	protected void onResume() {
		super.onResume();
		resumeEmulator();
	}

	@Override
	protected void onStop() {
		super.onStop();

		SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
		editor.putString("lastPickedGame", lastPickedGame);
		editor.commit();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putString("currentGame", currentGame);
	}

	@SuppressWarnings("deprecation")
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_QUIT_GAME:
			return createQuitGameDialog();
		case DIALOG_LOAD_STATE:
			return createLoadStateDialog();
		case DIALOG_SAVE_STATE:
			return createSaveStateDialog();
		}
		return super.onCreateDialog(id);
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);

		switch (id) {
		case DIALOG_QUIT_GAME:
		case DIALOG_LOAD_STATE:
		case DIALOG_SAVE_STATE:
			pauseEmulator();
			break;
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == quickLoadKey) {
			quickLoad();
			return true;
		}
		if (keyCode == quickSaveKey) {
			quickSave();
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_BACK && currentGame != null) {
			showDialog(DIALOG_QUIT_GAME);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		if (!isMenuShowing) {
			isMenuShowing = true;
			pauseEmulator();
		}
		menu.setGroupVisible(R.id.GAME_MENU, currentGame != null);
		return true;
	}

	@Override
	public void onOptionsMenuClosed(Menu menu) {
		super.onOptionsMenuClosed(menu);

		if (isMenuShowing) {
			isMenuShowing = false;
			resumeEmulator();
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (isMenuShowing) {
			isMenuShowing = false;
			resumeEmulator();
		}

		switch (item.getItemId()) {
		case R.id.menu_open:
			onLoadROM();
			return true;

		case R.id.menu_settings:
			startActivityForResult(new Intent(this, GamePreferences.class),
					REQUEST_SETTINGS);
			return true;

		case R.id.menu_reset:
			emulator.reset();
			return true;

		case R.id.menu_save_state:
			showDialog(DIALOG_SAVE_STATE);
			return true;

		case R.id.menu_load_state:
			if (mIsPremium) {
				showDialog(DIALOG_LOAD_STATE);
			} else {
				onUpgradeAppButtonClicked(null);
			}

			return true;

		case R.id.menu_close:
			unloadROM();
			return true;

		case R.id.menu_quit:
			finish();
			return true;
		case R.id.menu_moreapp:
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri
					.parse("https://play.google.com/store/apps/developer?id=Amy%20Janicke"));
			startActivity(i);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	// User clicked the "Upgrade to Premium" button.
	public void onUpgradeAppButtonClicked(View arg0) {
		Log.d(TAG,
				"Upgrade button clicked; launching purchase flow for upgrade.");
		setWaitScreen(true);

		/*
		 * TODO: for security, generate your payload here for verification. See
		 * the comments on verifyDeveloperPayload() for more info. Since this is
		 * a SAMPLE, we just use an empty string, but on a production app you
		 * should carefully generate this.
		 */
		String payload = "upgrademoba";

		mHelper.launchPurchaseFlow(this, SKU_PREMIUM, RC_REQUEST,
				mPurchaseFinishedListener, payload);
	}

	// Callback for when a purchase is finished
	IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
		public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
			Log.d(TAG, "Purchase finished: " + result + ", purchase: "
					+ purchase);

			// if we were disposed of in the meantime, quit.
			if (mHelper == null)
				return;

			if (result.isFailure()) {
				complain("Error purchasing: " + result);
				setWaitScreen(false);
				return;
			}
			if (!verifyDeveloperPayload(purchase)) {
				complain("Error purchasing. Authenticity verification failed.");
				setWaitScreen(false);
				return;
			}

			Log.d(TAG, "Purchase successful.");

			if (purchase.getSku().equals(SKU_PREMIUM)) {
				// bought the premium upgrade!
				Log.d(TAG, "Purchase is premium upgrade. Congratulating user.");
				alert("Thank you for upgrading to premium!");
				mIsPremium = true;
				updateUi();
				setWaitScreen(false);
			}
		}
	};

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		Log.d(TAG, "onActivityResult(" + request + "," + result + "," + data);
		if (mHelper == null)
			return;

		// Pass on the activity result to the helper for handling
		if (!mHelper.handleActivityResult(request, result, data)) {
			// not handled, so handle it ourselves (here's where you'd
			// perform any handling of activity results not related to in-app
			// billing...
			switch (request) {
			case REQUEST_BROWSE_ROM:
				if (result == RESULT_OK) {
					lastPickedGame = data
							.getStringExtra(FileChooser.EXTRA_FILEPATH);
					loadROM(lastPickedGame);
				}
				break;

			case REQUEST_BROWSE_BIOS:
				loadBIOS(result == RESULT_OK ? data
						.getStringExtra(FileChooser.EXTRA_FILEPATH) : null);
				break;

			case REQUEST_SETTINGS:
				loadGlobalSettings();
				break;
			}
		} else {
			Log.d(TAG, "onActivityResult handled by IABUtil.");
		}

	}

	public void onGameKeyChanged() {
		int states = 0;
		states |= keyboard.getKeyStates();
		states |= keypad.getKeyStates();

		if ((states & GAMEPAD_DIRECTION) != 0)
			trackball.reset();
		else
			states |= trackball.getKeyStates();

		// resolve conflict keys
		if ((states & GAMEPAD_LEFT_RIGHT) == GAMEPAD_LEFT_RIGHT)
			states &= ~GAMEPAD_LEFT_RIGHT;
		if ((states & GAMEPAD_UP_DOWN) == GAMEPAD_UP_DOWN)
			states &= ~GAMEPAD_UP_DOWN;

		emulator.setKeyStates(states);
	}

	public void onCancel(DialogInterface dialog) {
		resumeEmulator();
	}

	private boolean initEmulator(File datadir) {
		if (emulator != null)
			return true;

		// FIXME
		final String libdir = "/data/data/" + getPackageName() + "/lib";
		emulator = new Emulator();
		if (!emulator.initialize(libdir, datadir.getAbsolutePath()))
			return false;

		if (emuThread == null) {
			emuThread = new Thread() {
				public void run() {
					emulator.run();
				}
			};
			emuThread.start();
		}
		return true;
	}

	private void resumeEmulator() {
		if (resumeRequested++ == 0) {
			keyboard.reset();
			keypad.reset();
			trackball.reset();
			onGameKeyChanged();

			emulator.resume();
		}
	}

	private void pauseEmulator() {
		if (--resumeRequested == 0)
			emulator.pause();
	}

	private boolean copyAsset(File file) {
		if (file.exists())
			return true;

		InputStream in = null;
		OutputStream out = null;

		try {
			in = getAssets().open(file.getName());
			out = new FileOutputStream(file);

			byte[] buf = new byte[8192];
			int len;
			while ((len = in.read(buf)) > 0)
				out.write(buf, 0, len);

		} catch (Exception e) {
			e.printStackTrace();
			return false;

		} finally {
			try {
				if (out != null)
					out.close();
				if (in != null)
					in.close();
			} catch (IOException e) {
			}
		}
		return true;
	}

	private static int getScalingMode(String mode) {
		if (mode.equals("original"))
			return EmulatorView.SCALING_ORIGINAL;
		if (mode.equals("proportional"))
			return EmulatorView.SCALING_PROPORTIONAL;
		return EmulatorView.SCALING_STRETCH;
	}

	private void saveLastRunningGame(String game) {
		SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
		editor.putString("lastRunningGame", game);
		editor.commit();
	}

	private void loadGlobalSettings() {
		pauseEmulator();

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);
		emulator.setOption("autoFrameSkip",
				settings.getBoolean("autoFrameSkip", true));
		emulator.setOption("maxFrameSkips",
				Integer.toString(settings.getInt("maxFrameSkips", 2)));
		emulator.setOption("soundEnabled",
				settings.getBoolean("soundEnabled", true));

		trackball.setEnabled(settings.getBoolean("enableTrackball", false));
		keypad.setVisibility(settings.getBoolean("enableVirtualKeypad",
				GamePreferences.getDefaultVirtualKeypadEnabled(this)) ? View.VISIBLE
				: View.GONE);

		emulatorView.setScalingMode(getScalingMode(settings.getString(
				"scalingMode", "stretch")));

		// key bindings
		final int[] gameKeys = GamePreferences.gameKeys;
		final String[] prefKeys = GamePreferences.keyPrefKeys;
		final int[] defaultKeys = GamePreferences.getDefaultKeys(this);

		keyboard.clearKeyMap();
		for (int i = 0; i < prefKeys.length; i++) {
			keyboard.mapKey(gameKeys[i],
					settings.getInt(prefKeys[i], defaultKeys[i]));
		}

		// shortcut keys
		quickLoadKey = settings.getInt("quickLoad", 0);
		quickSaveKey = settings.getInt("quickSave", 0);

		resumeEmulator();
	}

	@SuppressWarnings("unused")
	private void switchToView(int id) {
		final int viewIds[] = { R.id.empty // ,
		// R.id.game
		};
		for (int i = 0; i < viewIds.length; i++) {
			findViewById(viewIds[i]).setVisibility(
					viewIds[i] == id ? View.VISIBLE : View.INVISIBLE);
		}
	}

	private Dialog createLoadStateDialog() {
		DialogInterface.OnClickListener l = new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				loadGameState(which);
				resumeEmulator();
			}
		};

		return new AlertDialog.Builder(this)
				.setTitle(R.string.load_state_title)
				.setItems(R.array.game_state_slots, l)
				.setOnCancelListener(this).create();
	}

	private Dialog createSaveStateDialog() {
		DialogInterface.OnClickListener l = new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				saveGameState(which);
				resumeEmulator();
			}
		};

		return new AlertDialog.Builder(this)
				.setTitle(R.string.save_state_title)
				.setItems(R.array.game_state_slots, l)
				.setOnCancelListener(this).create();
	}

	private Dialog createQuitGameDialog() {
		DialogInterface.OnClickListener l = new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case 0:
					resumeEmulator();
					onLoadROM();
					break;
				case 1:
					quickSave();
					saveLastRunningGame(currentGame);
					// fall through
				case 2:
					finish();
					break;
				}
			}
		};

		return new AlertDialog.Builder(this).setTitle(R.string.quit_game_title)
				.setItems(R.array.exit_game_options, l)
				.setOnCancelListener(this).create();
	}

	private void browseBIOS(String initial) {
		Intent intent = new Intent(this, FileChooser.class);
		intent.putExtra(FileChooser.EXTRA_TITLE,
				getResources().getString(R.string.title_select_bios));
		intent.putExtra(FileChooser.EXTRA_FILEPATH, initial);
		intent.putExtra(FileChooser.EXTRA_FILTERS, new String[] { ".bin",
				".rom" });
		startActivityForResult(intent, REQUEST_BROWSE_BIOS);
	}

	private boolean loadBIOS(String name) {
		if (name != null && emulator.loadBIOS(name)) {
			SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
					.edit();
			editor.putString("bios", name);
			editor.commit();
			return true;
		}

		final String biosFileName = name;
		DialogInterface.OnClickListener l = new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					browseBIOS(biosFileName);
					break;
				case DialogInterface.BUTTON_NEGATIVE:
					finish();
					break;
				}
			}
		};

		new AlertDialog.Builder(this)
				.setCancelable(false)
				.setTitle(R.string.load_bios_title)
				.setMessage(
						name == null ? R.string.bios_not_found
								: R.string.load_bios_failed)
				.setPositiveButton(R.string.browse_bios, l)
				.setNegativeButton(R.string.quit, l).show();

		return false;
	}

	private boolean loadROM(String fname) {
		return loadROM(fname, true);
	}

	private boolean loadROM(String fname, boolean failPrompt) {
		unloadROM();
		if (!emulator.loadROM(fname) && failPrompt) {
			Toast.makeText(this, R.string.load_rom_failed, Toast.LENGTH_SHORT)
					.show();
			return false;
		}
		currentGame = fname;
		// switchToView(R.id.game);
		hidePlaceholder();
		return true;
	}

	private void unloadROM() {
		if (currentGame != null) {
			emulator.unloadROM();
			currentGame = null;
			showPlaceholder();
			// switchToView(R.id.empty);
		}
	}

	private void onLoadROM() {
		Intent intent = new Intent(this, FileChooser.class);
		intent.putExtra(FileChooser.EXTRA_TITLE,
				getResources().getString(R.string.title_select_rom));
		intent.putExtra(FileChooser.EXTRA_FILEPATH, lastPickedGame);
		intent.putExtra(FileChooser.EXTRA_FILTERS, new String[] { ".gba",
				".bin", ".zip" });
		startActivityForResult(intent, REQUEST_BROWSE_ROM);
	}

	private void saveGameState(int slot) {
		String fname = getGameStateFile(currentGame, slot);
		emulator.saveState(fname);
	}

	private void loadGameState(int slot) {
		String fname = getGameStateFile(currentGame, slot);
		if (new File(fname).exists())
			emulator.loadState(fname);
	}

	private void quickSave() {
		saveGameState(0);
	}

	private void quickLoad() {
		loadGameState(0);
	}

	private void showPlaceholder() {
		emulatorView.setVisibility(View.INVISIBLE);
		placeholder.setVisibility(View.VISIBLE);
	}

	private void hidePlaceholder() {
		placeholder.setVisibility(View.GONE);
		emulatorView.setVisibility(View.VISIBLE);
	}

	private static String getGameStateFile(String name, int slot) {
		int i = name.lastIndexOf('.');
		if (i >= 0)
			name = name.substring(0, i);
		name += ".ss" + slot;
		return name;
	}

	public void checkUpdate() {
		String vname = "";
		int vcode = 10000;
		try {
			vname = this.getPackageManager().getPackageInfo(
					this.getPackageName(), 0).versionName;
			vcode = this.getPackageManager().getPackageInfo(
					this.getPackageName(), 0).versionCode;
		} catch (NameNotFoundException e) {
		}
		(new HttpClientHelper(new Observer() {

			@Override
			public void update(Observable observable, Object data) {
				// Log.e("HttpTask",data.toString());
				if (data instanceof JSONArray) {
					try {
						// Log.e("HttpTask",data.toString());
						JSONArray a = (JSONArray) data;
						final String msg = a.getString(1);
						final String link = a.getString(2);
						if (a.getInt(0) == 1) {
							runOnUiThread(new Runnable() {
								public void run() {
									AlertDialog.Builder ad = new AlertDialog.Builder(
											Agba.this);
									ad.setTitle("Update");
									ad.setMessage(msg);
									ad.setPositiveButton(
											"Update",
											new DialogInterface.OnClickListener() {

												@Override
												public void onClick(
														DialogInterface dialog,
														int which) {
													Intent app = new Intent(
															Agba.this,
															UpdateActivity.class);
													app.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
													app.putExtra("urlApp", link);
													startActivity(app);
												}
											});
									ad.setNegativeButton(
											"Skip",
											new DialogInterface.OnClickListener() {

												@Override
												public void onClick(
														final DialogInterface dialog,
														int which) {
													// TODO Auto-generated
													// method stub
													dialog.dismiss();
												}
											});
									ad.show();
								}
							});
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		})).update(getApplicationContext().getPackageName(), Locale
				.getDefault().getDisplayLanguage(), vcode, vname);
	}
}
