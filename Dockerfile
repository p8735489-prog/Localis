FROM eclipse-temurin:17-jdk-jammy

# Install essential build tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    unzip \
    git \
    python3 \
    python3-pip \
    libstdc++6 \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Set up Android SDK
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=$ANDROID_HOME
ENV PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH

# Download and install Android Command Line Tools
RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-12266719_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d $ANDROID_HOME/cmdline-tools/ && \
    mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Accept licenses and install SDK components
RUN yes | sdkmanager --licenses >/dev/null 2>&1

# Install required SDK components
RUN sdkmanager "platform-tools" \
    "platforms;android-35" \
    "build-tools;35.0.0" \
    "cmake;3.22.1" \
    "ndk;27.0.12077973" \
    "cmdline-tools;latest"

# Set work directory
WORKDIR /workspace

# Build command: copy project, build APK
# Usage:
#   docker build -t localis-builder .
#   docker run --rm -v $(pwd):/workspace localis-builder ./gradlew assembleRelease --no-daemon
