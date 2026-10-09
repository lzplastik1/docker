# Flexisip (proxy + b2bua sip-bridge) built from source.
# Dependencies follow the official docker/bc-dev-debian12 image of the Flexisip repository.
# Build needs network access to gitlab.linphone.org (submodules), ~8 GB RAM and 30-60 minutes.
FROM debian:12

ARG FLEXISIP_VERSION=2.6.2
ARG NJOBS=4

RUN apt-get -y update \
 && apt-get -y install --no-install-recommends \
      ca-certificates git cmake make ninja-build g++ python3 python3-pystache python3-six yasm doxygen \
      libsystemd-dev libssl-dev libboost-dev libboost-system-dev libboost-thread-dev libcpp-jwt-dev \
      libhiredis-dev libjansson-dev libjsoncpp-dev libnghttp2-dev libsqlite3-dev libpq-dev \
      libmariadb-dev libmariadb-dev-compat libsnmp-dev libxerces-c-dev libsrtp2-dev libgsm1-dev \
      libopus-dev libmbedtls-dev libspeex-dev libspeexdsp-dev libxml2-dev libvpx-dev \
 && rm -rf /var/lib/apt/lists/*

RUN git clone --branch ${FLEXISIP_VERSION} --recurse-submodules \
      https://gitlab.linphone.org/BC/public/flexisip.git /src/flexisip \
 && cmake -S /src/flexisip -B /src/build -G Ninja \
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
