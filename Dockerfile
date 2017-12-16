FROM clojure:lein-alpine-onbuild as builder
RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" githack.jar

FROM debian:wheezy

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    bison flex gcc groff bsdmainutils make autotools-dev automake autoconf \
    libncursesw5-dev ncurses-dev libsqlite3-dev sqlite3 \
    update-inetd telnetd xinetd locales ca-certificates default-jre && \
    locale-gen en_US.UTF-8

ADD https://github.com/paxed/dgamelaunch/archive/master.tar.gz /dgamelaunch/
RUN cd /dgamelaunch/ && tar xf master.tar.gz --strip-component=1 && \
    sed -i -e "s/-lrt/-lrt -pthread/" configure.ac && \
    ./autogen.sh --enable-sqlite --enable-shmem --with-config-file=/opt/nethack/nethack.alt.org/etc/dgamelaunch.conf && \
    make && ./dgl-create-chroot && \
    sed -i -e 's/^\(\s*r).*\)$/\1 (disabled)/' /opt/nethack/nethack.alt.org/dgl_menu_main_anon.txt && \
    cd / && rm -rf /dgamelaunch && \
    sqlite3 /opt/nethack/nethack.alt.org/dgldir/dgamelaunch.db \
            'alter table dglusers add turns integer default 0; alter table dglusers add meta text;' && \
    chmod 0666 /opt/nethack/nethack.alt.org/dgldir/dgamelaunch.db

ADD https://github.com/neoascetic/nh343-nao/archive/master.tar.gz /nh343-nao/
RUN cd /nh343-nao/ && tar xf master.tar.gz --strip-component=1 && \
    sed -i -e "/^CFLAGS/s/-O/-O2 -fomit-frame-pointer/" sys/unix/Makefile.src && \
    sed -i -e "/^CFLAGS/s/-O/-O2 -fomit-frame-pointer/" sys/unix/Makefile.utl && \
    sed -i -e "/rmdir \.\/-p/d" sys/unix/Makefile.top && \
    make all && make install && \
    cp /lib/x86_64-linux-gnu/libncurses* /opt/nethack/nethack.alt.org/lib/x86_64-linux-gnu/ && \
    cd / && rm -rf /nh343-nao/

RUN apt-get remove --auto-remove --purge -y \
    bison flex gcc groff git bsdmainutils make autotools-dev automake autoconf && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

COPY nethack/bothack.nh343rc /opt/nethack/nethack.alt.org/dgl-default-rcfile.nh343
COPY nethack/dgamelaunch.conf /opt/nethack/nethack.alt.org/etc/
COPY nethack/dgamelaunch.xinetd /etc/xinetd.d/dgamelaunch
COPY --from=builder /usr/src/app/githack.jar /githack/githack.jar

VOLUME ["/opt/nethack/nethack.alt.org/dgldir", "/opt/nethack/nethack.alt.org/nh343/var"]
EXPOSE 8000
CMD service xinetd start && exec java -jar /githack/githack.jar
