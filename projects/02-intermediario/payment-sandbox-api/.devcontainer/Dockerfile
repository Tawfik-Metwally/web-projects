FROM eclipse-temurin:25.0.4_7-jdk-noble

RUN apt-get update \
    && apt-get install --yes --no-install-recommends sudo \
    && printf 'ubuntu ALL=(root) NOPASSWD:ALL\n' > /etc/sudoers.d/ubuntu \
    && chmod 0440 /etc/sudoers.d/ubuntu \
    && mkdir --parents /home/ubuntu/.m2 \
    && chown --recursive ubuntu:ubuntu /home/ubuntu/.m2 \
    && rm --recursive --force /var/lib/apt/lists/*

WORKDIR /workspace

USER ubuntu

CMD ["sleep", "infinity"]
