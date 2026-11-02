# Backend image for Render (free web service, 512 MB RAM).
# Render builds this automatically from the backend/ folder on every push.

# ---------- build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first, so they are cached when only source code changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package

# ---------- run ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as an unprivileged user.
RUN useradd --system --uid 10001 appuser
COPY --from=build /workspace/target/course-finder.jar app.jar
USER appuser

# Memory tuned for a 512 MB instance:
#  MaxRAMPercentage=75   heap up to ~384 MB, leaving room for metaspace and threads
#  UseSerialGC           smallest GC footprint for a single small CPU
#  TieredStopAtLevel=1   faster startup on a slow shared CPU
# Override on Render by setting JAVA_TOOL_OPTIONS in the environment.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
