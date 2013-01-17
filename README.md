MapDB
===============

MapDB provides concurrent TreeMap and HashMap backed by disk storage or off-heap-memory.
It is a fast, scalable and easy to use embedded Java database engine. It is tiny (160KB jar),
yet packed with features such as transactions, space efficient serialization, instance cache
and transparent compression/encryption. It also has outstanding performance rivaled only by
native embedded db engines.

MapDB is free as speech and free as beer under [Apache License 2.0](https://github.com/jankotek/MapDB/blob/master/doc/license.txt).
More information can be found on [MapDB website](http://www.mapdb.org)

Intro
======
MapDB uses Maven build system. There is snapshot repository updated every a few days.
To use it add code bellow to your `pom.xml`. You may also download binaries
[directly](https://oss.sonatype.org/content/repositories/snapshots/org/mapdb/mapdb/).

    <repositories>
        <repository>
            <id>mapdb-snapshots</id>
            <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>org.mapdb</groupId>
            <artifactId>mapdb</artifactId>
            <version>0.9-SNAPSHOT</version>
                </dependency>
    </dependencies>


Hello world; it opens TreeMap backed by file in temp directory, file is discarded after JVM exit:

    import org.mapdb.*;
    ConcurrentSortedMap treeMap = DBMaker.newTempTreeMap()

    //and now use disk based Map as any other Map
    treeMap.put(111,"some value")


More advanced example with configuration and journaled transaction.
    import org.mapdb.*;

    //Configure and open database using builder pattern.
    //All options are available with code auto-completion.
    DB db = DBMaker.newFileDB(new File("testdb"))
        .closeOnJvmShutdown()
        .encryptionEnable("password")
        .make();

    //open existing an collection (or create new)
    ConcurrentNavigableMap<Integer,String> map = db.getTreeMap("collectionName");

    map.put(1,"one");
    map.put(2,"two");
    //map.keySet() is now [1,2]

    db.commit();  //persist changes into disk

    map.put(3,"three");
    //map.keySet() is now [1,2,3]
    db.rollback(); //revert recent changes
    //map.keySet() is now [1,2]

    db.close();

MapDB has very power-full API.
But for 99% cases you need just two classes:

[DBMaker](http://www.mapdb.org/apidocs/org/mapdb/DBMaker.html) is builder style factory class for configuring and opening
an database. It has handfull of static 'newXXX' method for opening database in particular storage mode.

[DB](http://www.mapdb.org/apidocs/org/mapdb/DB.html) represents and storage. It has methods for accessing Maps and
other collections. It also controls DB lifecycle with commit, rollback and close methods.


Development
===========
MapDB uses Kotlin programming language for some performance unit tests.
Simple modifications can be developed in any IDE just fine,
but for best experience you need Intellij Idea with Kotlin plugin.
Please follow this page [to setup Kotlin IDE](http://confluence.jetbrains.net/display/Kotlin/Getting+Started).

Pull requests are welcomed. We are relaxed about documentation, examples and unit tests.
Production code should fit MapDB style; most importantly to be compact and with unit tests.

What you should know
====================
* Transaction journal can be disabled, this will speedup writes. However without transactions
store gets corrupted easily when not closed correctly.

* MapDB assumes your data-model is immutable. Mutable keys or values will work, but may lead to unexpected results.

* MapDB relies on mapped memory heavily. And best NIO implementation is usually in latest JDK7.
Sometimes upgrading JVM miraculously fixes a problem.

* There are two collections TreeMap (B+Tree) and HashMap (HTree). TreeMap is
optimized for small keys, HashMap works best with larger key.

* MapDB does not run defrag on background. You need to call `DB.defrag()` from time to time. (TODO defrag not yet implemented)

* MapDB uses unchecked exceptions. All `IOException` are wrapped into unchecked `IOError`.


Support
=======
For anything with stack-trace you should create [new bug report](https://github.com/jankotek/MapDB/issues/new).
Small feature request should also go into bug tracker. Please always attach code to illustrate your problem.
Push requests with failing unit test case would be appreciated.

For specific support questions such as 'how do I open...' use [StackOverflow](http://stackoverflow.com/) with 'MapDB' tag.
We monitor these questions.

There is [mail-group](mailto:mapdb@googlegroups.com) with [archive](http://groups.google.com/group/mapdb).
For general discussion and abstract questions (such as "can MapDB support transactional software memory?").
Also questions about performance and data-modeling should go into mail-group.

Last option is to [contact me directly](mailto:jan at kotek dot net).
I prefer public bug tracker and mail-group so others can find answers as well.
Unless you specify your question as confidential, I may forward it to public mail-group.

MapDB is a hobby project and my time is limited.
Please always attach code to illustrate/reproduce your problem, so I can fix it efficiently.
If you have some exotic configuration, I may need your assistance with remote access and bug reproduction.
For hard to reproduce problems I would strongly suggest to record JVM execution with
[Chronon](http://www.chrononsystems.com/learn-more/products-overview) and submit the record together with a bug report.