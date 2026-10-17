# Flexisip (proxy + b2bua sip-bridge) built from source.
# Dependencies follow the official docker/bc-dev-debian12 image of the Flexisip repository.
# Build needs network access to gitlab.linphone.org (submodules), ~8 GB RAM and 30-60 minutes.
FROM debian:12

ARG FLEXISIP_VERSION=2.6.2
ARG NJOBS=4
# Optional Debian mirror replacing deb.debian.org, e.g. https://mirrors.ustc.edu.cn
# (must serve both /debian and /debian-security).
ARG DEBIAN_MIRROR=

# The base image has no CA certificates yet: fetch them without TLS peer checks
# (packages are still verified by apt's GPG signatures), then use TLS normally.
RUN if [ -n "${DEBIAN_MIRROR}" ]; then \
      sed -i "s|http://deb.debian.org|${DEBIAN_MIRROR}|g" /etc/apt/sources.list.d/debian.sources; \
    fi \
 && apt-get -o Acquire::https::Verify-Peer=false -y update \
 && apt-get -o Acquire::https::Verify-Peer=false -y install --no-install-recommends ca-certificates \
 && apt-get -y update \
 && apt-get -y install --no-install-recommends \
      ca-certificates git cmake make ninja-build g++ python3 python3-pystache python3-six yasm doxygen \
      libsystemd-dev libssl-dev libboost-dev libboost-system-dev libboost-thread-dev libcpp-jwt-dev \
      libhiredis-dev libjansson-dev libjsoncpp-dev libnghttp2-dev libsqlite3-dev libpq-dev \
      libmariadb-dev libmariadb-dev-compat libsnmp-dev libxerces-c-dev libsrtp2-dev libgsm1-dev \
      libopus-dev libmbedtls-dev libspeex-dev libspeexdsp-dev libxml2-dev libvpx-dev \
 && rm -rf /var/lib/apt/lists/*

# Fetch sources in their own layer, retrying: a flaky proxy can drop TLS connections, and a
# failed build below then reuses this layer instead of downloading everything again.
RUN git config --global http.version HTTP/1.1 \
 && ok=; for i in 1 2 3 4 5; do \
      git clone --branch ${FLEXISIP_VERSION} https://gitlab.linphone.org/BC/public/flexisip.git /src/flexisip \
        && ok=1 && break; \
      rm -rf /src/flexisip; echo "clone failed, retry $i"; sleep 15; \
    done; [ -n "$ok" ] \
 && cd /src/flexisip \
 && ok=; for i in 1 2 3 4 5 6 7 8 9 10; do \
      git submodule update --init --recursive --jobs 4 && ok=1 && break; \
      echo "submodule update failed, retry $i"; sleep 15; \
    done; [ -n "$ok" ]

# Flexisip needs gcc >= 13 or clang >= 19; Debian 12 ships gcc 12, so build with clang-19 like
# the upstream bc-dev-debian12 image. Installed after the sources layer to keep it cached.
RUN apt-get -y update \
 && apt-get -y install --no-install-recommends clang-19 \
 && rm -rf /var/lib/apt/lists/*

RUN cmake -S /src/flexisip -B /src/build -G Ninja \
      -DCMAKE_C_COMPILER=clang-19 \
      -DCMAKE_CXX_COMPILER=clang++-19 \
      -DCMAKE_BUILD_TYPE=Release \
      -DCMAKE_INSTALL_PREFIX=/opt/belledonne-communications \
      -DSYSCONF_INSTALL_DIR=/etc \
      -DENABLE_UNIT_TESTS=OFF \
      -DENABLE_SANITIZERS=OFF \
      -DENABLE_STRICT=OFF \
 && cmake --build /src/build -j ${NJOBS} \
 && cmake --build /src/build --target install \
 && rm -rf /src

ENV PATH=/opt/belledonne-communications/bin:$PATH

RUN mkdir -p /etc/flexisip /var/opt/belledonne-communications/flexisip/b2bua

COPY render-config.py entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/entrypoint.sh /usr/local/bin/render-config.py

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["proxy"]
