# Standalone EveKit Synchronization Manager

A synchronization manager is an EveKit component which ensures that account data is periodically downloaded and stored for each eligible EveKit account.  The synchronization manager also ensures that account snapshots are generated on a regular basis.  There are many ways to implement a synchronization manager depending on scale and redundancy requirements.  However, the current requirements of the public instance of EveKit are rather modest.  So for now we've implemented a straightforward single process synchronization manager (this module).  

The rest of this guide describes how to configure, build and deploy the EveKit Standalone Synchronization Manager.

## What the synchronization manager is supposed to do...

The synchronization manager has five responsibilities:

1. Scan for accounts which are eligible for synchronization and make sure they get scheduled for an update.
2. Dispatch eligible accounts for synchronization when scheduler resources are available.
3. Detect and kill stuck synchronization processes.
4. Delete accounts eligible for deletion.
5. Schedule and dispatch daily account snapshot dumps.

Several configuration parameters control when accounts are eligible for synchronization, when accounts are eligible for deletion, and how frequently account snapshots are generated.  See the configuration section below for more details.

The standalone synchronization manager runs out of the ```SyncManager``` class in a single process.  This class runs a scheduler which schedules events representing account synchronization, account deletion, and snapshot generation.  Each event has a time limit and tracker which records the status of the event.  The scheduler dispatches events according to type, and removes events when their associated tracker has completed, or if the event exceeds its time limit.  In the latter case, the task associated with the event (if any) is killed.

There are currently three event types: SYNC, DELETE and SNAPSHOT.  Each event type has a dedicated dispatcher with a dedicated thread scheduling queue (i.e. ```ExecutorService```).  This ensures that the different event types don't compete with each other for resources.  It is important to keep this separation as DELETE and SNAPSHOT events can take significant time to complete (for very large accounts) and we want to avoid delaying SYNC events so that we don't miss EVE Online API data updates.

The synchronization manager needs of an EveKit instance can be met by running the ```SyncManager``` as a standalone process.  However, for production deployments you'll want to monitor and restart this process as needed.  Code to do this is **not** included in this module, however there are examples showing how to do this below.

## Configuration

The standalone synchronization manager requires the setting and substitution of several parameters which control database and event management settings.  Since the standalone synchronization manager is normally built with [Maven](http://maven.apache.org), configuration is handled by setting or overriding properties in your local Maven settings.xml file.  The following configuration parameters should be set:

| Parameter | Meaning |
|-----------|---------|
|enterprises.orbital.evekit.sync_mgr.db.properties.url|Hibernate JDBC connection URL for properties|
|enterprises.orbital.evekit.sync_mgr.db.properties.user|Hibernate JDBC connection user name for properties|
|enterprises.orbital.evekit.sync_mgr.db.properties.password|Hibernate JDBC connection password for properties|
|enterprises.orbital.evekit.sync_mgr.db.properties.driver|Hibernate JDBC driver class name for properties|
|enterprises.orbital.evekit.sync_mgr.db.properties.dialect|Hibernate dialect class name for properties|
|enterprises.orbital.evekit.sync_mgr.db.account.url|Hibernate JDBC connection URL for account info|
|enterprises.orbital.evekit.sync_mgr.db.account.user|Hibernate JDBC connection user name for account info|
|enterprises.orbital.evekit.sync_mgr.db.account.password|Hibernate JDBC connection password for user info|
|enterprises.orbital.evekit.sync_mgr.db.account.driver|Hibernate JDBC driver class name for account info|
|enterprises.orbital.evekit.sync_mgr.db.account.dialect|Hibernate dialect class name for account info|
|enterprises.orbital.evekit.snapshot.directory|Local directory where snapshots should be stored|
|enterprises.orbital.evekit.site_agent|Site agent string to be used when contacting the EVE Online XML servers (e.g. "EveKit/1.0.0 (https://evekit.orbital.enterprises; )" )|
|enterprises.orbital.evekit.sync_attempt_separation|The number of milliseconds that must elapse between attempts to synchronize a single account.  **NOTE:** EveKit always honors the EVE Online API cache timers, regardless of the value you set here.|
|enterprises.orbital.evekit.sync_terminate_delay|Number of milliseconds a synchronization attempt is allowed to remain unfinished before it is force terminated.  A sane value is usually one hour.|
|enterprises.orbital.evekit.snapshot.interval|The number of milliseconds between account snapshots.  The public instance of EveKit sets this to 24 hours.|

As with all EveKit components, two database connections are required: one for retrieving general settings for system and user accounts; and, one for retrieving user account and model information.  These can be (and often are) the same database.  Only the database settings are mandatory.  All other parameters have sane defaults.

At build and deploy time, the parameters above are substituted into the following files:

* src/main/resources/META-INF/persistence.xml
* src/main/resources/SyncMgrStandalone.properties

If you are not using Maven to build, you'll need to substitute these settings manually.

## Build

We use [Maven](http://maven.apache.org) to build all EveKit modules.  EveKit dependencies are released and published to [Maven Central](http://search.maven.org/).  The EveKit standalone synchronization manager uses release branches, but is not deployed to Maven Central.  To build this module, clone this repository and use "mvn install".  Make sure you have set all required configuration parameters before building (as described in the previous section).

## Running the Manager

The standalone manager is designed to be run as a single process (run the ```SyncManager``` class).  For production deployments, you'll want to do a little better than this by detecting manager failure and restarting as needed.  The public instance of EveKit uses the following "service" style shell script:

```
#!/bin/bash
#
# Run the main sync loop.  Record the pid so that we can setup monit to automatically restart us if we fail.
#
case $1 in
   start)
      export MAVEN_OPTS=-Xmx2048m
      cd /home/orbital/sync/evekit-sync-mgr-standalone
      echo $$ > /home/orbital/sync/sync.pid
      exec 2>&1 mvn exec:java -Dexec.mainClass="enterprises.orbital.evekit.sync.SyncManager" -Djava.util.logging.config.file=/home/orbital/sync/logging.properties 1> /home/orbital/sync/stderr.out
      ;;
     stop)
       kill `cat /home/orbital/sync/sync.pid` ;;
     *)
       echo "usage: run-sync {start|stop}" ;;
 esac
 exit 0
```

We run this script out of [Monit](https://mmonit.com/monit/) which ensures that the synchronization manager is always running.

## Getting Help

The best place to get help is on the [Orbital Forum](https://groups.google.com/forum/#!forum/orbital-enterprises).
