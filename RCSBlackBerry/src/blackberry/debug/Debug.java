//#preprocess

/* *************************************************
 * Copyright (c) 2010 - 2010
 * HT srl,   All rights reserved.
 * Project      : RCS, RCSBlackBerry_lib
 * File         : Debug.java
 * Created      : 26-mar-2010
 * *************************************************/
package blackberry.debug;

import java.util.Date;

import net.rim.device.api.i18n.DateFormat;
import net.rim.device.api.system.Alert;
import net.rim.device.api.system.DeviceInfo;
import net.rim.device.api.system.LED;
import net.rim.device.api.system.Memory;
import net.rim.device.api.system.MemoryStats;
import net.rim.device.api.util.NumberUtilities;
import blackberry.config.Cfg;
import blackberry.evidence.Evidence;
import blackberry.fs.Path;

/**
 * The Class Debug.
 */
public final class Debug {

    public static int level = 6;

    //static DebugWriter debugWriter;
    static Evidence logInfo;

    private static boolean logToDebugger;
    private static boolean logToFlash;

    private static boolean enabled = true;
    private static boolean init = false;

    //                  1234567890123456
    String className = "                ";

    int actualLevel = 6;

    public static final int COLOR_BLUE_LIGHT = 0x00C8F0FF; //startRecorder
    public static final int COLOR_RED = 0x00ff1029; // error
    public static final int COLOR_ORANGE = 0x00ff5e1b; // crysis
    public static final int COLOR_GREEN = 0x001fbe1a;
    public static final int COLOR_GREEN_LIGHT = 0x0044DC4C; // evidence
    public static final int COLOR_YELLOW = 0x00f3f807; // sync
    public static final int COLOR_WHITE = 0xffffffff;

    /*
     * prior: priorita', da 6 bassa a bassa, level LEVEL = {
     * TRACE,DEBUG,INFO,WARN, ERROR, FATAL }
     */

    /**
     * Instantiates a new debug.
     * 
     * @param className_
     *            the class name_
     */
    public Debug(final String className_) {
        this(className_, DebugLevel.VERBOSE);
    }

    /**
     * Instantiates a new debug.
     * 
     * @param className_
     *            the class name_
     * @param classLevel
     *            the class level
     */
    public Debug(final String className_, final int classLevel) {

        //#ifdef DBC
        Check.requires(className_ != null, "className_ void");
        Check.requires(className_.length() > 0, "className_ empty");
        //#endif

        final int len = className_.length();

        //#ifdef DBC
        Check.requires(len <= className.length(), "Classname too long");
        //#endif

        className = className_ + className.substring(len);
        actualLevel = Math.min(classLevel, level);

        //trace("Level: " + actualLevel);
    }

    public synchronized static boolean isInitialized() {
        return init;
    }

    /**
     * Inits the.
     * 
     * @param logToDebugger_
     *            the log to console
     * @param logToSD_
     *            the log to SD
     * @param logToFlash_
     *            the log to internal Flash
     * @param logToEvents_
     *            the log to events_
     * @return true, if successful
     */
    public synchronized static boolean init() {
        //#ifdef DBC
        //Check.requires(Path.isInizialized(), "init: Path not initialized");
        //#endif

        if (isInitialized()) {
            return false;
        }

        /*
         * boolean enabled = true; //#ifndef DEBUG enabled = false; //#endif if
         * (!enabled) { return false; }
         */

        Debug.logToDebugger = Cfg.DEBUG_OUT;
        Debug.logToFlash = Cfg.DEBUG_FLASH;

        if (DeviceInfo.isSimulator()) {
            Debug.logToFlash = false;
        }

        Path.makeDirs();

        if (logToFlash) {
            DebugWriter debugWriter = DebugWriter.getInstance();
            debugWriter.logToFile = (logToFlash);

            if (!debugWriter.started()) {
                try {
                    debugWriter.start();
                } catch (Exception ex) {
                    //#ifdef DEBUG
                    System.out.println("Catch the exception");
                    ex.printStackTrace();
                    //#endif
                }
            }
        }

        init = true;
        return true;
    }

    /**
     * Stop.
     */
    public static synchronized void stop() {
        init = false;
        DebugWriter debugWriter = DebugWriter.getInstance();
        if (debugWriter.started()) {
            //#ifdef DBC
            Check.asserts(debugWriter.isAlive(), "should be alive");
            //#endif
            debugWriter.requestStop();

            try {
                debugWriter.join();
            } catch (final InterruptedException e) {
            }
            //#ifdef DBC
            Check.asserts(!debugWriter.isAlive(), "shouldn't be alive");
            //#endif
        }
    }

    //#ifdef DEBUG
    /**
     * Trace.
     * 
     * @param message
     *            the message
     */
    public void trace(final String message) {

        if (enabled) {
            trace("-   - " + className + " | " + message, DebugLevel.VERBOSE);
        }

    }

    /**
     * Info.
     * 
     * @param message
     *            the message
     */
    public void info(final String message) {

        if (enabled) {
            trace("-INF- " + className + " | " + message,
                    DebugLevel.INFORMATION);
        }

    }

    /**
     * Warn.
     * 
     * @param message
     *            the message
     */
    public void warn(final String message) {

        if (enabled) {
            trace("-WRN- " + className + " | " + message, DebugLevel.WARNING);
        }

    }

    /**
     * Warn.
     * 
     * @param message
     *            the message
     */
    public void warn(final Exception ex) {

        if (enabled) {
            trace("-WRN- " + className + " | " + ex, DebugLevel.WARNING);
        }

    }

    /**
     * Error.
     * 
     * @param message
     *            the message
     */
    public void error(final String message) {

        if (enabled) {
            ledFlash(Debug.COLOR_RED);
            trace("#ERR# " + className + " | " + message, DebugLevel.ERROR);
        }

    }

    public void error(String message, Exception ex) {
        if (enabled) {
            ledFlash(Debug.COLOR_RED);

            trace("#ERR# " + className + " | " + message + " " + ex,
                    DebugLevel.ERROR);
            ex.printStackTrace();
        }
    }

    /**
     * Error.
     * 
     * @param message
     *            the message
     */
    public void error(final Exception ex) {

        if (enabled) {
            ledFlash(Debug.COLOR_RED);

            trace("#ERR# " + className + " | " + ex, DebugLevel.ERROR);
            ex.printStackTrace();
        }

    }

    /**
     * Fatal.
     * 
     * @param message
     *            the message
     */
    public void fatal(final String message) {

        if (enabled) {
            trace("#FTL# " + className + " | " + message, DebugLevel.CRITICAL);
        }

    }

    public void fatal(final Exception ex) {

        if (enabled) {
            trace("#FTL# " + className + " | " + ex, DebugLevel.CRITICAL);
            ex.printStackTrace();
        }

    }

    //#endif

    private void logToDebugger(final String string, final int priority) {
        System.out.println(Thread.currentThread().getName() + " " + string);
    }

    private void logToWriter(final String message, final int priority) {

        DebugWriter debugWriter = DebugWriter.getInstance();

        //#ifdef DBC
        Check.requires(debugWriter != null, "logToFile: debugWriter null");
        Check.requires(logToFlash, "!logToFlash");
        //#endif

        boolean error = (priority <= DebugLevel.ERROR);

        final boolean ret = debugWriter.append(message, priority, error);

        if (ret == false) {
            // procedura in caso di mancata scrittura
            if (Debug.logToDebugger) {
                logToDebugger("debugWriter.append returns false",
                        DebugLevel.ERROR);
            }
        }
    }

    /*
     * Scrive su file il messaggio, in append. Pu� scegliere se scrivere su
     * /store o su /SDCard Alla partenza dell'applicativo la SDCard non �
     * visibile.
     */
    private void trace(final String message, final int level) {
        //#ifdef DBC
        Check.requires(level > 0, "level >0");
        //#endif

        if (level > actualLevel || message == null) {
            return;
        }

        if (logToDebugger) {
            logToDebugger(message, level);
        }

        if (!isInitialized()) {
            return;
        }

        if (logToFlash) {
            final long timestamp = (new Date()).getTime();
            /*
             * Calendar calendar = Calendar.getInstance(); calendar.setTime(new
             * Date());
             */

            final DateFormat formatTime = DateFormat
                    .getInstance(DateFormat.TIME_FULL);

            final String time = formatTime.formatLocal(timestamp).substring(0,
                    8);
            String milli = NumberUtilities.toString(timestamp % 1000, 10, 3);

            /*
             * String time = calendar.get(Calendar.HOUR)+":"+
             * calendar.get(Calendar.MINUTE)+":"+ calendar.get(Calendar.SECOND);
             */
            logToWriter(time + " " + milli + " " + message, level);
        }

    }

    public static void ledFlash(int color) {
        ledStart(color);
        //playSound();
        ledStop();
    }

    public static void ledStart(int color) {
        try {
            LED.setConfiguration(LED.LED_TYPE_STATUS, 1000, 1000,
                    LED.BRIGHTNESS_12);
            LED.setColorConfiguration(1000, 1000, color);
            LED.setState(LED.STATE_BLINKING);
            ;

        } catch (final Exception ex) {

        }

    }

    public static void ledStop() {
        try {
            LED.setState(LED.STATE_OFF);

        } catch (final Exception ex) {

        }
    }

    public static void playSound(short[] sound) {
        try {
            Alert.startAudio(sound, 100);
        } catch (Exception e) {

        }
    }

    public static void playSound() {
        short[] fire = { 1400, 15 };
        try {
            Alert.startAudio(fire, 100);
            Alert.startVibrate(100);
        } catch (Exception e) {

        }
    }

    public static void playSoundError(int value) {
        short[] errorPlay = { 1400, 50, 700, 100 };
        short[] countPlay = { 600, 100, 0, 0 };
        playSound(errorPlay);
        for (int i = 0; i < value; i++) {
            playSound(countPlay);
        }
    }

    public static void playSoundOk(int value) {
        short[] okPlay = { 700, 50, 1400, 100 };
        short[] countPlay = { 1800, 100, 0, 0 };
        playSound(okPlay);
        for (int i = 0; i < value; i++) {
            playSound(countPlay);
        }
    }

    public void traceMemory() {
        MemoryStats objects = Memory.getObjectStats();

        Evidence.info("Memory STATS OBJECTS:  allocated="
                + objects.getAllocated() + " free=" + objects.getFree()
                + " numAllocated=" + objects.getObjectCount() + "  size="
                + objects.getObjectSize());

        /*
         * objects = Memory.getRAMStats();
         * Evidence.info("Memory STATS RAM:  allocated=" +
         * objects.getAllocated() + " free=" + objects.getFree() +
         * " numAllocated=" + objects.getObjectCount() + "  size=" +
         * objects.getObjectSize());
         */

    }
}
