# WLed Binding

This openHAB binding allows you to auto discover and use LED strings from the WLED project found here:
<https://github.com/Aircoookie/WLED>

To watch what the binding does, enter this in to the openHAB console log:set TRACE org.openhab.binding.wled

## Supported Things

| Thing Type ID | Description |
|-|-|
| `wled` | Use this for RGB and RGBW strings. |

## Discovery

The auto discovery will work with this binding if your network supports mDNS.
If it fails to find your WLED, you can manually add a `wled` thing by using the UI or textual methods.
The full example section gives everything needed to quickly setup using textual config.

## Thing Configuration

| Parameter | Description |
|-|-|
| `address`| The URL to your WLED device. Example is `http://192.168.0.2:80` |
| `pollTime`| How often you want the states of the LED fetched in case you make changes with a non openHAB app or web browser or the light is auto changing FX. |

## Channels

| Channel | Type | Description |
|-|-|-|
| `masterControls` | Color | Gives you control over the WLED like it is a normal light. Tag this control for Alexa or Google/Nest to change the lights instantly to any color, brightness or on/off state that you ask for regardless of what mode the light is in. |
| `primaryColor` | Color | The primary color used in FX. |
| `secondaryColor` | Color | The secondary color used in FX. |
| `palettes` | String | A list of palettes you can select from. |
| `fx` | String |  A list of Effects you can select from. |
| `speed` | Dimmer | Changes the speed of the loaded effect. |
| `intensity` | Dimmer | Changes the intensity of the loaded effect. |
| `presets` | String |  A list of presets you can select from.  |
| `presetCycle` | Switch | Turns on automatic changing from one preset to the next. |
| `presetDuration` | Dimmer | How long it takes to change from one preset to the next with `presetCycle` turned ON. |
| `transformTime` | Dimmer | How long it takes to transform/morph from one look to the next. |
| `sleep` | Switch | Turns on the sleep timer. |

## Full Example

*.things

```
Thing wled:wled:ChristmasTree "My Christmas Tree" @ "Lights" [address="http://192.168.0.4:80"]
```

*.items

```
Color XmasTree_MasterControls "Christmas Tree" ["Lighting"] {channel="wled:wled:ChristmasTree:masterControls"}
Color XmasTree_Primary "Primary Color"    {channel="wled:wled:ChristmasTree:primaryColor"}
Color XmasTree_Secondary   "Secondary Color"  {channel="wled:wled:ChristmasTree:secondaryColor"}
String XmasTree_FX       "FX"        <text>{channel="wled:wled:ChristmasTree:fx"}
String XmasTree_Palette  "Palette"   <colorwheel>    {channel="wled:wled:ChristmasTree:palettes"}
String XmasTree_Presets  "Preset"    <text> {channel="wled:wled:ChristmasTree:presets"}
Dimmer XmasTree_Speed    "FX Speed"  <time>  {channel="wled:wled:ChristmasTree:speed"}
Dimmer XmasTree_Intensity "FX Intensity" {channel="wled:wled:ChristmasTree:intensity"}
Switch XmasTree_PresetCycle "presetCycle" <time> {channel="wled:wled:ChristmasTree:presetCycle"}
Dimmer XmasTree_PresetDuration "presetDuration" <time> {channel="wled:wled:ChristmasTree:presetDuration"}
Dimmer XmasTree_TransformTime "presetTransformTime" <time> {channel="wled:wled:ChristmasTree:transformTime"}
Switch XmasTree_Sleep    "Sleep"     <moon> {channel="wled:wled:ChristmasTree:sleep"}

```

*.sitemap

```
        Text label="XmasLights" icon="rgb"{
            Switch item=XmasTree_MasterControls
            Slider item=XmasTree_MasterControls
            Colorpicker item=XmasTree_MasterControls
            Switch item=XmasTree_Sleep
            Colorpicker item=XmasTree_Primary
            Colorpicker item=XmasTree_Secondary            
            Selection item=XmasTree_FX
            Selection item=XmasTree_Palette
            Selection item=XmasTree_Presets
            Default item=XmasTree_Speed  
            Default item=XmasTree_Intensity            
            Default item=XmasTree_PresetCycle  
            Default item=XmasTree_PresetDuration 
            Default item=XmasTree_TransformTime
        }
        
```
