# WLed Binding

This Openhab 2 binding allows you to auto find and use the WLED project found here:
<https://github.com/Aircoookie/WLED>

## Supported Things

| Thing Type ID | Description |
|-|-|
| `wled` | Use this for a RGB string. |
| `mqttBroker` | This is used to connect directly to your MQTT broker ie Mosquitto. Then each wled thing needs to select one of these as a bridge. Your MQTT user and pass are needed in the config for this to connect. |


Currently only implemented RGB strings. Have not look at what is needed to support RGBW strings that include discrete white leds as I have not looked into what is different for those setups.

## Discovery

The auto discovery will work with this binding. First setup a `mqttBroker` thing and supply your mqtt details. Then after that connects and shows up as Online, you can then search and auto find any wled strings and strips. This works by finding any topic entries in your broker after /wled/XXXXXXX/ so if you wish to use nice friendly names instead of a mac address hash, go into the WLED setup panel and change the settings needed to publish to the desired name or number of your choosing.

## Binding Configuration

You can also use textual config to setup your wled strings, the most important thing to understand is that you MUST create the uniqueID for the thing to be the exact same as the mqtt topic. Example is if you see MQTT topics being produced as wled/0eb121/.... you would then make the uniqueID be equal to 0eb121. As mentioned above you can change this to be a nicer name instead of a number by changing the setup of WLED before you start setting up openhab.


```

Bridge wled:mqttBroker:001 [ADDR="tcp://localhost:1883", MQTT_USERNAME="user", MQTT_PASSWORD="password"]
{
Thing wled 0eb121 "My Christmas Tree" //Christmas tree lights
}

```

## Common Issues / FAQ

If you need help be sure to look at the logs FIRST. You can enable debug log output with this command in the console..

```
log:set DEBUG org.openhab.binding.wled
```

After checking the logs, it is also helpful to look at the mqtt topics with this command:


```
mosquitto_sub -u usernamehere -P passwordhere -p 1883 -v -t 'wled/#'
```


If changing the colour is not working or taking a while, be sure to 1. Set the FX to SOLID and 2. Set the transition time to the minimum so any changes you wish are made right away. The colour selection is ignored if the lights are in a FX where the primary colour is ignored.

If the drop down Selection lists for FX, Presets and Palettes are not working in the iOS application, you may need to add a mapping to the sitemap declaration for it to work. This does not happen in BasicUI or the Andriod app.

## Thing Configuration

PR welcome.

## Channels

PR welcome.

## Full Example

*.items

```

Color XmasTree "Christmas Tree" ["Lighting"] {channel="wled:wled:001:0eb121:colour"}
Switch XmasTreeSwitch   "on/off"    {channel="wled:wled:001:0eb121:colour"}
Dimmer XmasTreeDimmer   "level"     {channel="wled:wled:001:0eb121:colour"}
Number XmasTreeFX       "FX"      <text> {channel="wled:wled:001:0eb121:fx"}
Number XmasTreePalette  "Palette"   <colorwheel>    {channel="wled:wled:001:0eb121:palettes"}
Number XmasTreePresets  "Preset" <text> {channel="wled:wled:001:0eb121:presets"}
Dimmer XmasTreeSpeed    "FX Speed"  <time>  {channel="wled:wled:001:0eb121:speed"}
Dimmer XmasTreeIntensity "FX Intensity" {channel="wled:wled:001:0eb121:intensity"}
Switch XmasTreePresetCycle "presetCycle" <time> {channel="wled:wled:001:0eb121:presetCycle"}
Dimmer XmasTreePresetDuration "presetDuration" <time> {channel="wled:wled:001:0eb121:presetDuration"}
Dimmer XmasTreePresetTime "presetTransformTime" <time> {channel="wled:wled:001:0eb121:presetTransformTime"}
Switch XmasTreeSleep    "Sleep"     <moon> {channel="wled:wled:001:0eb121:sleep"}

```

*.sitemap

```
        Text label="XmasTree" icon="rgb"{
            Switch item=XmasTreeSwitch 
            Slider item=XmasTreeDimmer 
            Default item=XmasTree 
            Selection item=XmasTreeFX
            Selection item=XmasTreePalette
            Selection item=XmasTreePresets
            Default item=XmasTreeSpeed  
            Default item=XmasTreeIntensity
            Default item=XmasTreeSleep
            Default item=XmasTreePresetCycle  
            Default item=XmasTreePresetDuration 
            Default item=XmasTreePresetTime
        }
        
```


