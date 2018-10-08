NDN OCR Android Application  
used in the Computation offloading with ICN [ACM ICN 2018 Demo](http://conferences.sigcomm.org/acm-icn/2018/proceedings/icn18posterdemo-final4.pdf)
================================================

This version of the Android app supports Device-to-Device (D2D) communications and therefore the possibilty of offloading computation to other smarphones for Optical Character Recognition (OCR).

## Prerequisites

To compile code, the following is necessary

- Recent version of [Android SDK](http://developer.android.com/sdk/index.html)

Example script for Ubuntu 16.04 to get all dependencies, download SDK and NDK:

    sudo apt -q update
    sudo apt -qy upgrade
    sudo apt-get install -y build-essential git openjdk-8-jdk unzip ruby ruby-rugged
    sudo apt-get install -y lib32stdc++6 lib32z1 lib32z1-dev

    mkdir android-sdk-linux
    cd android-sdk-linux
    wget https://dl.google.com/android/repository/sdk-tools-linux-3859397.zip
    unzip sdk-tools-linux-3859397.zip
    rm sdk-tools-linux-3859397.zip

    export ANDROID_HOME=`pwd`
    export PATH=${PATH}:${ANDROID_HOME}/tools/bin:${ANDROID_HOME}/platform-tools

    echo "y" | sdkmanager "platform-tools"
    sdkmanager "platforms;android-28" "ndk-bundle"

    cd ndk-bundle
    git clone https://github.com/named-data-mobile/android-crew-staging crew.dir

    CREW_OWNER=named-data-mobile crew.dir/crew install target/sqlite target/openssl target/boost
    CREW_OWNER=named-data-mobile crew.dir/crew install target/ndn_cxx target/nfd

    cd ..

The above `crew` scripts will install pre-compiled versions of sqlite, openssl, and boost libraries.
For more details about the crew tool, refer to README-dev.md.

## Building

    git clone https://github.com/srene/icn_demo_Android
    echo sdk.dir=`pwd`/android-sdk-linux > icn_demo_Android/local.properties
    echo ndk.dir=`pwd`/android-sdk-linux/ndk-bundle >> icn_demo_Android/local.properties
    cd icn_demo_Android

    # Build in release mode (you will need to have proper signing keys configured, see README-dev.md)
    ./gradlew assembleRelease

    # Build in debug mode
    ./gradlew assembleDebug

You can also automatically install debug/release the app to the connected phone

    # build and install release version (will require signing key configuration)
    ./gradlew installRelease

    # build and install debug version
    ./gradlew installDebug

Note that you can limit architectures being built using `NDK_BUILD_ABI` variable.  For example,

    export NDK_BUILD_ABI=armeabi-v7a,x86_64

will limit build to `armeabi-v7a` and `x86_64`.

By default, the build script will try to parallelize build to the number of CPUs.  This can be
overridden using `NDK_BUILD_PARALLEL` variable.

## D2D Communications

In this project, we use WiFi Direct to provide D2D connectivity between users. However WiFi Direct Android implementation  requires user participation to accept every connection. In order to allow computation offloading without requiring user participation, and thus making it transparent and seamless to users, we use a hybrid mode WiFi Direct / legacy WiFi to provide D2D communications.
Worker devices, that offer its computation, create a WiFi Direct network using the *WiFi Direct Autonomous Mode* with only one participant and becoming automatically the Group Owner (GO), without waiting for the connection with any other devices nearby. This way, these devices create a WiFi Direct group prior to any user joining to it. When users that want to request OCR computation discover these worker devices, instead of joining it using the WiFi Direct API, they join the network using the WiFi legacy mode in the same way they could connect to an Access Point (AP) or another user offering connectivity in hotspot mode.  This means the destination node connects to the GO's device as if it was a normal WiFi AP. This way, since it is not a proper WiFi Direct connection, no  user intervention is required to accept the connection and the transfer happens transparently. Using this mode, neither the user that requests OCR computation nor the user that offers computation needs to accept the connection being possible to offload computation even with the smartphone with the screen off. 

Once the users leave the network the source nodes shut down the connection and only create the WiFi Direct network when it discovers other users willing to connect in the vicinity.

### Users' discovery
Android implements its own service discovery for WiFi Direct connections based on [DNS](https://developer.android.com/training/connect-devices-wirelessly/nsd-wifi-direct), but it presents several issues, such as instability or unreliable results. 
To solve this issue, we implemented another service discovery module, based on the so-well known and stable [Bluetooth Low energy (BLE)](https://developer.android.com/guide/topics/connectivity/bluetooth-le) beacons technology in order to 
exchange information related to the device's applications prior to the WiFi Direct connection.
Users can hear for these beacons (in a very low energy consumption mode) to discover other users with the same application and happy to offer computation offloading.
Once the beacon is discovered, two devices can start a Generic Attribute Profile (GATT) connection to exchange information about the  service. This connection does not require user interaction and can be performed totally transparent to the user end with the power  consumption optimisation of the BLE protocol.


## Source code 

The source code of the app is organized as following:

```
.
|-- app
|   |-- libs
|   |-- release
|   `-- src
|       `-- main
|           |-- java
|           |   `-- uk
|           |       `-- ac
|           |           `-- ucl
|           |               `-- ndnocr
|           |                   |-- App.java
|           |                   |-- MainActivity.java
|           |                   |-- data
|           |                   |   |-- Content.java
|           |                   |   |-- DatabaseHandler.java
|           |                   |   |-- NdnOcrService.java
|           |                   |   `-- OcrText.java
|           |                   |-- net
|           |                   |   |-- ble
|           |                   |   |   |-- BLEServiceDiscovery.java
|           |                   |   |   |-- BluetoothUtils.java
|           |                   |   |   |-- ByteUtils.java
|           |                   |   |   |-- Constants.java
|           |                   |   |   |-- GattServerCallback.java
|           |                   |   |   |-- StringUtils.java
|           |                   |   |   `-- TimeProfile.java
|           |                   |   `-- wifi
|           |                   |       |-- WifiDirectHotSpot.java
|           |                   |       |-- WifiLink.java
|           |                   |       `-- WifiServiceDiscovery.java
|           |                   |-- ui
|           |                   |   |-- fragments
|           |                   |   |   `--   ..
|           |                   |   `-- splash
|           |                   |       `--   ..
|           |                   `-- utils
|           |                       `--   ..
|           |-- jni
|           |   |-- NFD 
|           |   |   `--   ..
|           |   |-- ndn-cxx
|           |   |   `--   ..
|           |   |-- ndn-cxx-android
|           |   |   `--   ..
|           |   `-- nfd-android
|           |       `--   ..
|           |-- libs 
|           |   `--   ..
|           |-- obj
|           |   `--   ..
|           |-- play
|           |   `--   ..
|           `-- res 
|               `--   ..
`-- gradle
    `-- wrapper

```

In the folder *app/src/main/java/uk/ac/ucl/ndnocr* folder there is the source code related with the Android application in java. This code is organized as following:

* ndnocr: It includes the code of the main Android activity.
* ndnocr/data: This folder include all the code related with the OCR service. The *NdnOcrService.java* class is the service that runs on background and implements all the NDN  communications between devices.
* ndnocr/net: This folder contains the network features (ble for the BLE service discovery and wifi for the WiFI direct and legacy WiFi connection.
* ndnocr/ui: This folder all the code related with the user interface and the corresponding fragments.
* ndnocr/utils: Some 


This app also includes the NFD and ndn-cxx libraries native code necessary to provide NDN capabilites in the folder *app/src/main/jni/*. This code is originary from the official NFD android [port](https://github.com/named-data-mobile/NFD-android) 

## Running the app


*Note: We detected some issues when running on Android Oreo version. It may have some problems disconnecting from the network and connecting to the WiFi Direct network when discovering other nodes, however if the device is not connected to any WiFi network when running the app, it should connect to other devices with no problem.*
