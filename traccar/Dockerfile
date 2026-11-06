# The official Traccar image with its main program swapped for one built with mv710g.patch.
# Everything else (web app, libraries, Java) is the official image's own.
ARG TRACCAR_VERSION=6.17.0
FROM traccar/traccar:${TRACCAR_VERSION}
COPY tracker-server.jar /opt/traccar/tracker-server.jar
