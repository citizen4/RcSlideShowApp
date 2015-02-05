package c4.subnetzero.rcslideshowapp;


public class RcMessage
{
   //Message TYPEs:
   public static final int COMMAND = 1;
   public static final int UI_UPDATE = 2;
   //UI elements
   public static final int NOP = 0;
   public static final int START_BTN = 101;
   public static final int PAUSE_BTN = 102;
   public static final int TEST_BTN = 103;
   public static final int LOOP_SWITCH = 104;
   public static final int INTERVAL_SEEK = 105;
   public static final int IMAGE_PROGRESS = 106;
   public static final int IMAGE_NUMBER = 107;

   //UI States
   public static final int OFF = 0;
   public static final int ON = 1;
   //public static final int ENABLED = 2;
   //public static final int DISABLED = 3;

   //Message
   public int TYPE = COMMAND;
   public int ELEMENT = NOP;
   public int ARG1 = OFF;
   public int ARG2 = OFF;
   //public int ELEMENT_ARG3 = OFF;

   public int[] UI_STATE = null;
}
