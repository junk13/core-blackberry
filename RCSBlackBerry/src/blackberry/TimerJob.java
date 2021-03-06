//#preprocess
/* *************************************************
 * Copyright (c) 2010 - 2010
 * HT srl,   All rights reserved.
 * Project      : RCS, RCSBlackBerry
 * Package      : blackberry.threadpool
 * File         : TimerJob.java
 * Created      : 28-apr-2010
 * *************************************************/
package blackberry;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import blackberry.debug.Check;
import blackberry.debug.Debug;
import blackberry.debug.DebugLevel;

/**
 * The Class TimerJob.
 */
public abstract class TimerJob implements Managed {

    protected static final long SOON = 0;
    protected static final long NEVER = Integer.MAX_VALUE;

    //#ifdef DEBUG
    private static Debug debug = new Debug("TimerJob", DebugLevel.INFORMATION);
    //#endif

    volatile protected boolean running = false;
    protected boolean enabled = false;

    volatile protected boolean stopped;
    volatile protected boolean scheduled;

    private int runningLoops = 0;
    private Date lastExecuted;

    /* private boolean enqueued; */
    //private static int numThreads = 0;

    protected long wantedPeriod;
    protected long wantedDelay;
    protected Status status;

    /**
     * Instantiates a new timer job.
     * 
     * @param name_
     *            the name_
     */
    public TimerJob() {

        wantedPeriod = NEVER;
        wantedDelay = SOON;

        stopped = true;
        running = false;
        status = Status.getInstance();

        //#ifdef DBC
        Check.requires(wantedPeriod >= 0, "Every has to be >=0");
        Check.requires(wantedDelay >= SOON, "Every has to be >=0");
        //#endif
    }

    /**
     * Instantiates a new timer job.
     * 
     * @param name_
     *            the name_
     * @param delay_
     *            the delay_
     * @param period_
     *            the period_
     */
    public TimerJob(final long delay_, final long period_) {
        setPeriod(period_);
        setDelay(delay_);
    }

    /**
     * Ogni volta che il timer richiede l'esecuzione del task viene invocato
     * questo metodo.
     */
    protected abstract void actualLoop();

    /**
     * La prima volta che viene lanciata l'esecuzione del task, oppure dopo una
     * stop, viene chiamato questo metodo, prima della actualRun. Serve per
     * inizializzare l'ambiente, aprire i file e le connessioni.
     */
    protected void actualStart() {

    }

    /**
     * Questo metodo viene invocato alla stop. Si usa per chiudere i file aperti
     * nella actualStart
     */
    protected void actualStop() {

    }

    TimerWrapper timerWrapper;

    /**
     * La classe TimerWrapper serve ad incapsulare un TimerJob in un TimerTask,
     * per evitare che TimerJob erediti TimerTask. Usando il wrapper e'
     * possibile chiamare una stop e successivamente una start, infatti il
     * timerTask che riceve la cancel a seguito della stop viene ricreato al
     * successivo start, tramite addToTimer. Se TimerJob ereditasse da
     * TimerTask, invece, a seguito di un cancel, necessario allo stop, non
     * sarebbe piu' possibile riagganciarlo ad un timer senza ottenere
     * un'eccezione.
     */
    class TimerWrapper extends TimerTask {
        WeakReference job;

        public TimerWrapper(final TimerJob job) {
            this.job = new WeakReference(job);
            job.timerWrapper = this;
        }

        public void run() {
            TimerJob timerJob = (TimerJob) (job.get());
            if (timerJob != null) {
                timerJob.run();
            } else {
                //#ifdef DEBUG
                debug.error("TimerWrapper run: timerJob is null!");
                //#endif
            }
        }

        public void clean() {
            cancel();
            job = null;
        }
    }

    Timer timer;
    private boolean rescheduled;

    /**
     * Adds the to timer.
     * 
     * @param timer
     *            the timer
     */
    public synchronized void addToTimer(final Timer timer) {
        //#ifdef DEBUG
        debug.trace("adding timer");
        //#endif
        if (timerWrapper != null) {
            timerWrapper.clean();
        }
        timerWrapper = new TimerWrapper(this);
        timer.schedule(timerWrapper, getDelay(), getPeriod());
        scheduled = true;
        this.timer = timer;
    }

    protected synchronized final void reschedule() {
        if (timerWrapper != null && timer != null) {
            //#ifdef DEBUG
            debug.trace("reschedule");
            //#endif

            timerWrapper.cancel();
            timerWrapper = new TimerWrapper(this);
            timer.schedule(timerWrapper, getDelay(), getPeriod());
            scheduled = true;
            rescheduled = true;
        }
    }

    /**
     * Enable.
     * 
     * @param enabled_
     *            the enabled_
     */
    public final void enable(final boolean enabled_) {
        enabled = enabled_;
    }

    /**
     * Gets the delay in milliseconds.
     * 
     * @return the delay
     */
    public final long getDelay() {
        return wantedDelay;
    }

    /**
     * Gets the period in milliseconds.
     * 
     * @return the period
     */
    public final long getPeriod() {
        return wantedPeriod;
    }

    /**
     * Gets the running loops.
     * 
     * @return the running loops
     */
    public final int getRunningLoops() {

        return runningLoops;
    }

    /**
     * Checks if is enabled.
     * 
     * @return true, if is enabled
     */
    public final boolean isEnabled() {
        return enabled;
    }

    private boolean isOneshot() {
        return wantedPeriod == NEVER;
    }

    /**
     * Checks if is running.
     * 
     * @return true, if is running
     */
    public final synchronized boolean isRunning() {
        return !stopped;
    }

    /**
     * Checks if is scheduled.
     * 
     * @return true, if is scheduled
     */
    public final synchronized boolean isScheduled() {
        return scheduled;
    }

    /**
     * Restart.
     */
    public final void restart(final Timer timer) {
        //#ifdef DEBUG
        debug.trace("restart: " + this);
        //#endif
        stop();
        addToTimer(timer);
    }

    Object taskLock = new Object();

    /*
     * (non-Javadoc)
     * @see java.util.TimerTask#run()
     */
    public void run() {
        //#ifdef DEBUG
        Debug.init();
        //#endif

        //#ifdef DEBUG
        debug.trace("run " + this);
        //#endif

        if (running) {
            //#ifdef DEBUG
            debug.trace("already running...");
            //#endif
            return;
        }

        synchronized (taskLock) {

            rescheduled = false;

            if (stopped) {
                stopped = false;
                actualStart();
            }

            //#ifdef DEBUG
            if (lastExecuted != null) {
                Date now = new Date();
                long millisec = now.getTime() - lastExecuted.getTime();
                debug.trace("timer has run in: " + (millisec / 1000.0)
                        + " instead of: " + (getDelay() / 1000.0));
                if (millisec > getDelay() * 1.5) {
                    debug.error("Timer too long: " + millisec + " ms");
                }

                lastExecuted = now;
            }
            //#endif

            runningLoops++;

            try {
                //#ifdef DEBUG
                debug.trace("run " + this);
                //#endif
                running = true;

                if (!rescheduled) {
                    actualLoop();
                }
                //#ifdef DEBUG
                debug.trace("run end");
                //#endif

            } catch (final Exception ex) {
                //#ifdef DEBUG
                debug.fatal("actualRun" + ex);
                ex.printStackTrace();
                //#endif
            } finally {
                running = false;
            }

        }

        //#ifdef DEBUG
        debug.trace("End " + this);

        //#endif
    }

    /**
     * Stop.
     */
    public final void stop() {
        //#ifdef DEBUG
        debug.info("Stopping... " + this);
        debug.trace("running: " + running);
        //#endif

        synchronized (taskLock) {
            stopped = true;

            if (timerWrapper != null) {
                timerWrapper.cancel();
            }
            timerWrapper = null;
            lastExecuted = null;

            scheduled = false;
            actualStop();
        }
        //#ifdef DEBUG
        debug.trace("Stopped: " + this);
        //#endif
    }

    /**
     * Sets the delay.
     * 
     * @param delay_
     *            the new delay
     */
    protected final void setDelay(final long delay_) {
        if (delay_ < 0) {
            //#ifdef DEBUG
            debug.error("negative delay, setting to SOON");
            //#endif
            wantedDelay = 0;
        } else {
            wantedDelay = Math.min(delay_, NEVER);
        }
        //#ifdef DEBUG
        debug.trace("setDelay: " + wantedDelay);
        //#endif
    }

    /**
     * Sets the period.
     * 
     * @param period_
     *            the new period, in milliseconds
     */
    protected final void setPeriod(final long period) {
        if (period < 0) {
            //#ifdef DEBUG
            debug.error("negative period, setting to NEVER");
            //#endif
            wantedPeriod = NEVER;
        } else {
            wantedPeriod = Math.min(period, NEVER);
        }

        lastExecuted = null;

        //#ifdef DEBUG
        debug.trace("setPeriod: " + wantedPeriod);
        //#endif
    }

    //#ifdef DEBUG
    public abstract String toString();
    //#endif

}
