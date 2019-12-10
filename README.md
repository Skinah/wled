# WLed Binding

This Openhab 2 binding allows you to auto find and use the WLED project found here:
<https://github.com/Aircoookie/WLED>

## Supported Things

Currently only tested RGB strings, may need more work to support RGBW strings that include descrete white leds.

## Discovery

The auto discovery will work with this binding. First setup a mqtt broker thing and supply your mqtt details, then after that connects and shows Online, you can then search and auto find wled strings and strips. This works by finding any topic entries in your broker after /wled/XXXXXXX/

## Binding Configuration

You can also use textual config to setup your wled strings, the most important thing to understand is that you MUST create the uniqueID for the thing to be the same as the mqtt topic. Example is wled/0eb121/.... you would then make the uniqueID be equal to 0eb121.


```

Bridge wled:mqttBroker:001 [ADDR="tcp://localhost:1883", MQTT_USERNAME="user", MQTT_PASSWORD="password"]
{
Thing wled 0eb121 "My Christmas Tree" //Christmas tree lights
}

```


## Thing Configuration

PR welcome.

## Channels

PR welcome.

## Full Example

*.items

```
Switch XmasTreeOnOff {channel="wled:wled:001:0eb121:colour"}
Dimmer XmasTreeBrightness {channel="wled:wled:001:0eb121:colour"}
Color XmasTree "My Christmas Tree" ["Lighting"] {channel="wled:wled:001:0eb121:colour"}
Number XmasTreeFX "FX" {channel="wled:wled:001:0eb121:fx"}
Number XmasTreePalette "Palette"  {channel="wled:wled:001:0eb121:palettes"}
Number XmasTreePresets "Presets"  {channel="wled:wled:001:0eb121:presets"}
Dimmer XmasTreeSpeed  "FX Speed" {channel="wled:wled:001:0eb121:speed"}
Dimmer XmasTreeIntensity "FX Intensity"  {channel="wled:wled:001:0eb121:intensity"}
Switch XmasTreeSleep "Sleep"  {channel="wled:wled:001:0eb121:sleep"}
Switch XmasTreePresetCycle "presetCycle"  {channel="wled:wled:001:0eb121:presetCycle"}
Dimmer XmasTreePresetDuration "presetDuration"  {channel="wled:wled:001:0eb121:presetDuration"}
Dimmer XmasTreePresetTime "presetTransformTime"  {channel="wled:wled:001:0eb121:presetTransformTime"}

```

*.sitemap

```
Text label="XmasTree" icon="rgb"{
            Default item=XmasTree 
            Selection item=XmasTreeFX //add mappings here
            Selection item=XmasTreePalette //add mappings here
            Selection item=XmasTreePresets //add mappings here
            Default item=XmasTreeSpeed  
            Default item=XmasTreeIntensity
            Default item=XmasTreeSleep
            Default item=XmasTreePresetCycle  
            Default item=XmasTreePresetDuration 
            Default item=XmasTreePresetTime
        }
        
```


