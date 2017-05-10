## android-external-device-example
Example project for interfacing with a IOX-USB using the Android Open Accessory protocol.

The example displays streaming Hours Of Service data and allows saving of generic Status Data.


## Setup
The application was developed and tested on a Nexus 7 tablet running Android 5.1.1.

The project was built with Android Studio 2.3.1.

To enable third-party data communication with the IOX-USB the following custom parameter must be set through MyGeotab:
>\<GoParameters>\<Parameter Description="Enable USB Data" Offset="164" Bytes="02"/>\</GoParameters>


## Refrences
This project is based on the Android Open Accessory framework developed by Embedded Artists found here:
http://www.embeddedartists.com/products/app/aoa_kit.php


