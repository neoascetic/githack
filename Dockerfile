FROM clojure:lein-alpine-onbuild as builder
RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" githack.jar

FROM debian:wheezy
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    bison flex gcc groff git bsdmainutils make autotools-dev automake autoconf \
    libncursesw5-dev ncurses-dev libsqlite3-dev sqlite3 \
    update-inetd telnetd xinetd locales ca-certificates default-jre && \
    locale-gen en_US.UTF-8

RUN git clone git://github.com/paxed/dgamelaunch.git && cd dgamelaunch && \
    sed -i -e "s/-lrt/-lrt -pthread/" configure.ac && \
    ./autogen.sh --enable-sqlite --enable-shmem --with-config-file=/opt/nethack/nethack.alt.org/etc/dgamelaunch.conf && \
    make && ./dgl-create-chroot && \
    sed -i -e 's/^\(\s*r).*\)$/\1 (disabled)/' /opt/nethack/nethack.alt.org/dgl_menu_main_anon.txt && \
    cd .. && rm -rf dgamelaunch

RUN git clone http://alt.org/nethack/nh343-nao.git && cd nh343-nao && \
    sed -i -e "/^CFLAGS/s/-O/-O2 -fomit-frame-pointer/" sys/unix/Makefile.src && \
    sed -i -e "/^CFLAGS/s/-O/-O2 -fomit-frame-pointer/" sys/unix/Makefile.utl && \
    sed -i -e "/rmdir \.\/-p/d" sys/unix/Makefile.top && \
    make all && make install && \
    cp /lib/x86_64-linux-gnu/libncurses* /opt/nethack/nethack.alt.org/lib/x86_64-linux-gnu/ && \
    cd .. && rm -rf nh343-nao

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
