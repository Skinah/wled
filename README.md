# WLed Binding

This openHAB binding allows you to auto discover and use LED strings from the WLED project found here:
<https://github.com/Aircoookie/WLED>

## Supported Things

| Thing Type ID | Description |
|-|-|
| `wled` | Use this for RGB and RGBW strings. |

## Discovery

The auto discovery will work with this binding if your network supports mDNS searches.
If it fails to work, you can manually add a WLED string by using the UI to manually add a `wled` thing.

## Thing Configuration

| Parameter | Description |
|-|-|
| `address`| The URL to your WLED device. Example is `http://192.168.0.2:80` |
| `pollTime`| How often you want the states of the LED fetched in case you make changes with a non openHAB app or web browser. |

## Channels

| Channel | Type | Description |
|-|-|-|
| `masterBrightness` | Dimmer | Changes the global brightness of the LED string. |
| `primaryColor` | Color | The primary color used in FX. |
| `secondaryColor` | Color | The secondary color used in FX. |
| `solidColor` | Color | Will change the primaryColor with any commands, the difference is that the FX is changed to SOLID first so you can use this tagged for Alexa or Google/Nest devices to change the lights instantly to any color you ask for. |
| `palettes` | String | A list of palettes you can select from. |
| `fx` | String |  A list of Effects you can select from. |
| `speed` | Dimmer | Changes the speed of the loaded effect. |
| `intensity` | Dimmer | Changes the intensity of the loaded effect. |
| `presets` | String |  A list of presets you can select from.  |
| `presetDuration` | Dimmer | How long it takes to change from one preset to the next with `presetCycle` turned ON. |
| `presetCycle` | Switch | Turns on automatic changing from one preset to the next. |
| `sleep` | Switch | Turns on the sleep timer. |

## Full Example

*.things

```
Thing wled:wled:ChristmasTree "My Christmas Tree" @ "Lights" [address="http://192.168.0.4:80"]
```

*.items

```

Dimmer XmasTree_Master  "Tree Brightness"   {channel="wled:wled:ChristmasTree:masterBrightness"}
Color XmasTree_Primary "Primary Color"    {channel="wled:wled:ChristmasTree:primaryColor"}
Color XmasTree_Secondary   "Secondary Color"  {channel="wled:wled:ChristmasTree:secondaryColor"}
Color XmasTree_Solid    "Christmas Tree" ["Lighting"] {channel="wled:wled:ChristmasTree:solidColor"}
String XmasTree_FX       "FX"        <text>{channel="wled:wled:ChristmasTree:fx"}
String XmasTree_Palette  "Palette"   <colorwheel>    {channel="wled:wled:ChristmasTree:palettes"}
String XmasTree_Presets  "Preset"    <text> {channel="wled:wled:ChristmasTree:presets"}
Dimmer XmasTree_Speed    "FX Speed"  <time>  {channel="wled:wled:ChristmasTree:speed"}
Dimmer XmasTree_Intensity "FX Intensity" {channel="wled:wled:ChristmasTree:intensity"}
Switch XmasTree_PresetCycle "presetCycle" <time> {channel="wled:wled:ChristmasTree:presetCycle"}
Dimmer XmasTree_PresetDuration "presetDuration" <time> {channel="wled:wled:ChristmasTree:presetDuration"}
Dimmer XmasTree_PresetTime "presetTransformTime" <time> {channel="wled:wled:ChristmasTree:presetTransformTime"}
Switch XmasTree_Sleep    "Sleep"     <moon> {channel="wled:wled:ChristmasTree:sleep"}

```

*.sitemap

```
        Text label="XmasLights" icon="rgb"{
            Switch item=XmasTree_Master
            Slider item=XmasTree_Master
            Colorpicker item=XmasTree_Primary
            Colorpicker item=XmasTree_Secondary
            Colorpicker item=XmasTree_Solid
            Selection item=XmasTree_FX
            Selection item=XmasTree_Palette
            Selection item=XmasTree_Presets
            Default item=XmasTree_Speed  
            Default item=XmasTree_Intensity
            Switch item=XmasTree_Sleep
            Default item=XmasTree_PresetCycle  
            Default item=XmasTree_PresetDuration 
            Default item=XmasTree_PresetTime
        }
        
```
