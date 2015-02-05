package c4.subnetzero.rcslideshowapp;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

public class RcSlideShowActivity extends Activity implements Handler.Callback
{
   private static final String LOG_TAG = "RcSlideShowActivity";
   private Handler mUiHandler;
   private BtRcManager mBtRcManager;
   private ImageButton mRcStateBtn;
   private TextView mRcStateLabel;
   private ViewGroup mRemoteGui;
   private TextView mImageText;
   private TextView mIntervalText;
   private Switch mLoopSwitch;
   private SeekBar mIntervalSeek;
   private ProgressBar mImageProgressBar;
   private ToggleButton mStartStopBtn;
   private ToggleButton mPauseResumeBtn;
   private ToggleButton mShowTestBtn;
   private int mIntervalSec = 3;
   private long mLastActionTimeStamp;
   //private boolean mIsBtEnabled;


   @Override
   public void onCreate(Bundle savedInstanceState)
   {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.main);
      setup();
   }

   @Override
   protected void onResume()
   {
      Log.d(LOG_TAG, "onResume()");
      super.onResume();

      mLastActionTimeStamp = System.currentTimeMillis();

      if (mBtRcManager != null) {
         mBtRcManager.start();
      }
   }

   @Override
   protected void onPause()
   {
      Log.d(LOG_TAG, "onPause()");
      super.onPause();

      if (mBtRcManager != null) {
         mBtRcManager.close();
      }
   }

   @Override
   protected void onDestroy()
   {
      Log.d(LOG_TAG, "onDestroy()");
      super.onDestroy();

   }

   @Override
   protected void onRestoreInstanceState(Bundle savedInstanceState)
   {
      Log.d(LOG_TAG, "onRestoreInstanceState()");
      super.onRestoreInstanceState(savedInstanceState);
   }

   @Override
   protected void onSaveInstanceState(Bundle outState)
   {
      Log.d(LOG_TAG, "onSaveInstanceState()");
      super.onSaveInstanceState(outState);
   }

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent data)
   {
      if (requestCode == BtRcManager.REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
         mBtRcManager.setState(BtRcManager.State.DISCONNECTED);
      }else {
         mBtRcManager.setState(BtRcManager.State.DISABLED);
      }
   }


   @Override
   public boolean handleMessage(Message msg)
   {
      switch (msg.what) {
         case BtRcManager.STATE_CHANGED:
            setRcState(mBtRcManager.getState());
            break;
         case BtRcManager.MESSAGE_RECEIVED:
            RcMessage rcMsg = (RcMessage) msg.obj;
            if (rcMsg.TYPE == RcMessage.UI_UPDATE) {
               setUiState(rcMsg.UI_STATE);
               break;
            }
            break;
         default:
            return false;
      }

      return true;
   }


   private void setup()
   {
      ActionBar mActionBar = getActionBar();
      mUiHandler = new Handler(this);
      mRemoteGui = (ViewGroup) findViewById(R.id.remote_gui);

      if (mActionBar != null) {
         LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
         @SuppressLint("InflateParams") View actionbarView = inflater.inflate(R.layout.actionbar, null);

         mRcStateBtn = (ImageButton) actionbarView.findViewById(R.id.rc_state_btn);
         mRcStateLabel = (TextView) actionbarView.findViewById(R.id.rc_state_label);

         mRcStateBtn.setOnClickListener(new View.OnClickListener()
         {
            @Override
            public void onClick(View v)
            {
               if (mBtRcManager.getState() == BtRcManager.State.DISCONNECTED) {
                  mBtRcManager.startConnection();
               } else {
                  mBtRcManager.close();
               }
            }
         });

         mActionBar.setDisplayShowHomeEnabled(false);
         mActionBar.setDisplayHomeAsUpEnabled(false);
         mActionBar.setDisplayUseLogoEnabled(false);
         mActionBar.setDisplayShowTitleEnabled(false);
         mActionBar.setDisplayShowCustomEnabled(true);

         mActionBar.setCustomView(actionbarView);
      }else {

         mRcStateBtn = (ImageButton) findViewById(R.id.rc_state_btn);
         mRcStateLabel = (TextView) findViewById(R.id.rc_state_label);

         mRcStateBtn.setOnClickListener(new View.OnClickListener()
         {
            @Override
            public void onClick(View v)
            {
               if (mBtRcManager.getState() == BtRcManager.State.DISCONNECTED) {
                  mBtRcManager.startConnection();
               } else {
                  mBtRcManager.close();
               }
            }
         });
      }

      mImageText = (TextView) findViewById(R.id.img_progress_label);
      mImageProgressBar = (ProgressBar) findViewById(R.id.img_progress_bar);

      mLoopSwitch = (Switch) findViewById(R.id.loop_switch);
      mLoopSwitch.setOnClickListener(mOnClickListener);

      mStartStopBtn = (ToggleButton) findViewById(R.id.start_stop_tgl);
      mStartStopBtn.setOnClickListener(mOnClickListener);

      mPauseResumeBtn = (ToggleButton) findViewById(R.id.pause_resume_tgl);
      mPauseResumeBtn.setOnClickListener(mOnClickListener);

      mShowTestBtn = (ToggleButton) findViewById(R.id.show_test_tgl);
      mShowTestBtn.setOnClickListener(mOnClickListener);

      mIntervalText = (TextView) findViewById(R.id.interval_text);
      mIntervalSeek = (SeekBar) findViewById(R.id.interval_seek);
      mIntervalSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
      {
         @Override
         public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
         {
            mIntervalText.setText("Interval: " + (fromUser ? (2 + progress) : progress) + " sec.");
         }

         @Override
         public void onStartTrackingTouch(SeekBar seekBar)
         {
         }

         @Override
         public void onStopTrackingTouch(SeekBar seekBar)
         {
            //Prevent too much action
            if (System.currentTimeMillis() - mLastActionTimeStamp < 1000) {
               return;
            }

            RcMessage commandMsg = new RcMessage();
            mIntervalSec = seekBar.getProgress() + 2;
            mIntervalText.setText("Interval: " + mIntervalSec + " sec.");
            commandMsg.ELEMENT = RcMessage.INTERVAL_SEEK;
            commandMsg.ARG1 = seekBar.getProgress();

            if (mBtRcManager.getState() == BtRcManager.State.CONNECTED) {
               mBtRcManager.sendMessage(commandMsg);
            }
            mLastActionTimeStamp = System.currentTimeMillis();
         }
      });

      mIntervalSeek.setProgress(mIntervalSec);

      mBtRcManager = new BtRcManager(this, mUiHandler, null);
   }

   private void setRcState(final BtRcManager.State newState)
   {
      float guiAlpha;
      int stateColor;
      Drawable stateDrawable;

      switch (newState){
         case DISABLED:
            stateColor = Color.RED;
            guiAlpha = 0.1f;
            stateDrawable = getResources().getDrawable(R.drawable.state_red_bg);
            break;
         case DISCONNECTED:
            stateColor = Color.GRAY;
            guiAlpha = 0.1f;
            stateDrawable = getResources().getDrawable(R.drawable.state_gray_bg);
            break;
         case CONNECTING:
            stateColor = Color.YELLOW;
            guiAlpha = 0.1f;
            stateDrawable = getResources().getDrawable(R.drawable.state_yellow_bg);
            break;
         case CONNECTED:
            stateColor = Color.GREEN;
            guiAlpha = 1.0f;
            stateDrawable = getResources().getDrawable(R.drawable.state_green_bg);
            break;
         default:
            return;
      }

      mRcStateLabel.setText(newState.toString());
      mRcStateLabel.setTextColor(stateColor);
      mRemoteGui.setAlpha(guiAlpha);
      mRcStateBtn.setBackground(stateDrawable);
   }


   private void setUiState(final int[] uiState)
   {
      int i = 0;
      while (i < uiState.length) {

         switch (uiState[i++]) {
            case RcMessage.START_BTN:
               mStartStopBtn.setEnabled(uiState[i++] == RcMessage.ON);
               mStartStopBtn.setChecked(uiState[i++] == RcMessage.ON);
               break;
            case RcMessage.PAUSE_BTN:
               mPauseResumeBtn.setEnabled(uiState[i++] == RcMessage.ON);
               mPauseResumeBtn.setChecked(uiState[i++] == RcMessage.ON);
               break;
            case RcMessage.TEST_BTN:
               mShowTestBtn.setEnabled(uiState[i++] == RcMessage.ON);
               mShowTestBtn.setChecked(uiState[i++] == RcMessage.ON);
               break;
            case RcMessage.LOOP_SWITCH:
               mLoopSwitch.setChecked(uiState[i++] == RcMessage.ON);
               break;
            case RcMessage.INTERVAL_SEEK:
               mIntervalSeek.setProgress(uiState[i++]);
               break;
            case RcMessage.IMAGE_NUMBER:
               mImageProgressBar.setMax(uiState[i++]);
               break;
            case RcMessage.IMAGE_PROGRESS:
               mImageProgressBar.setProgress(uiState[i]);
               mImageText.setText(String.format(getString(R.string.img_progress), uiState[i++], mImageProgressBar.getMax()));
               mImageProgressBar.setVisibility(uiState[i]);
               mImageText.setVisibility(uiState[i++]);
               break;
            default:
               i++;
               break;
         }
      }
   }

   private View.OnClickListener mOnClickListener = new View.OnClickListener()
   {
      @Override
      public void onClick(View v)
      {
         if (v instanceof CompoundButton) {
            CompoundButton btn = (CompoundButton) v;
            //keep visual state
            btn.toggle();
            //Prevent too much action
            if (System.currentTimeMillis() - mLastActionTimeStamp < 600) {
               return;
            }

            RcMessage commandMsg = new RcMessage();
            commandMsg.TYPE = RcMessage.COMMAND;

            switch (btn.getId()) {
               case R.id.start_stop_tgl:
                  commandMsg.ELEMENT = RcMessage.START_BTN;
                  break;
               case R.id.pause_resume_tgl:
                  commandMsg.ELEMENT = RcMessage.PAUSE_BTN;
                  break;
               case R.id.show_test_tgl:
                  commandMsg.ELEMENT = RcMessage.TEST_BTN;
                  break;
               case R.id.loop_switch:
                  commandMsg.ELEMENT = RcMessage.LOOP_SWITCH;
                  break;
               default:
                  return;
            }

            if (mBtRcManager.getState() == BtRcManager.State.CONNECTED) {
               mBtRcManager.sendMessage(commandMsg);
            }

            mLastActionTimeStamp = System.currentTimeMillis();
         }
      }
   };

}
