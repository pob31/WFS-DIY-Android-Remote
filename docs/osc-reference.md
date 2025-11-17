# WFS Control OSC Reference

Complete reference for OSC (Open Sound Control) messages used by WFS Control.

## Connection Details

- **Protocol**: OSC over UDP
- **Update Rate**: 50Hz (20ms throttling per parameter)
- **Bi-directional**: App sends and receives messages
- **Default Ports**:
  - Incoming (app receives): 8000
  - Outgoing (app sends): 8001

## Message Format

OSC messages follow this structure:
```
/address/path <type><argument1> <type><argument2> ...
```

### Data Types

- `i` = Integer (32-bit)
- `f` = Float (32-bit)
- `s` = String
- Example: `,iff` means integer, float, float

---

## Incoming Messages (Server → App)

Messages the app receives from the WFS-DIY server.

### Marker Position

#### Input Marker Position
```
/marker/positionXY <inputID:i> <x:f> <y:f>
```
- **inputID**: Input number (1-64)
- **x**: X coordinate in meters
- **y**: Y coordinate in meters
- **Example**: `/marker/positionXY 1 5.0 3.5`

#### Input Marker Name
```
/marker/name <inputID:i> <name:s>
```
- **inputID**: Input number (1-64)
- **name**: Display name (up to 24 characters)
- **Example**: `/marker/name 1 "Vocals"`

### Cluster Position

#### Cluster XY Position
```
/cluster/positionXY <clusterID:i> <x:f> <y:f>
```
- **clusterID**: Cluster number (1-10)
- **x**: X coordinate in meters
- **y**: Y coordinate in meters
- **Example**: `/cluster/positionXY 1 10.0 8.0`

#### Cluster Height
```
/cluster/positionZ <clusterID:i> <z:f>
```
- **clusterID**: Cluster number (1-10)
- **z**: Height in meters (relative to stage origin)
- **Example**: `/cluster/positionZ 1 4.5`

### Configuration

#### Number of Inputs
```
/inputs <count:i>
```
- **count**: Number of active inputs (0-64)
- **Example**: `/inputs 32`

#### Stage Dimensions
```
/stage/width <meters:f>
/stage/depth <meters:f>
/stage/height <meters:f>
```
- **meters**: Dimension in meters
- **Example**: `/stage/width 20.0`

#### Stage Origin
```
/stage/originX <meters:f>
/stage/originY <meters:f>
/stage/originZ <meters:f>
```
- **meters**: Offset from (0,0,0)
- **Example**: `/stage/originX 10.0`

### Input Parameters

All input parameter messages follow this pattern:
```
/remoteInput/<parameter> <inputID:i> <value>
```

#### Input Group Parameters

**Input Name**
```
/remoteInput/inputName <inputID:i> <name:s>
```

**Attenuation** (dB)
```
/remoteInput/attenuation <inputID:i> <dB:f>
```
- Range: -92.0 to 0.0

**Latency Compensation / Delay** (milliseconds)
```
/remoteInput/latencyCompensation <inputID:i> <ms:f>
```
- Range: -100.0 to +100.0

**Minimal Latency** (0 or 1)
```
/remoteInput/minimalLatency <inputID:i> <state:i>
```
- 0 = Acoustic Precedence, 1 = Minimal Latency

**Position X, Y, Z** (meters)
```
/remoteInput/positionX <inputID:i> <meters:f>
/remoteInput/positionY <inputID:i> <meters:f>
/remoteInput/positionZ <inputID:i> <meters:f>
```
- Range: -50.0 to +50.0

**Offset X, Y, Z** (meters)
```
/remoteInput/offsetX <inputID:i> <meters:f>
/remoteInput/offsetY <inputID:i> <meters:f>
/remoteInput/offsetZ <inputID:i> <meters:f>
```
- Range: -50.0 to +50.0

**Cluster** (cluster ID or -1 for none)
```
/remoteInput/cluster <inputID:i> <clusterID:i>
```
- Range: -1 to 10 (-1 = none, 1-10 = cluster number)

**Max Speed Active** (0 or 1)
```
/remoteInput/maxSpeedActive <inputID:i> <state:i>
```
- 0 = OFF, 1 = ON

**Max Speed** (m/s)
```
/remoteInput/maxSpeed <inputID:i> <speed:f>
```
- Range: 0.01 to 20.0

**Height Factor** (percentage)
```
/remoteInput/heightFactor <inputID:i> <percent:f>
```
- Range: 0.0 to 100.0

**Attenuation Law** (0 or 1)
```
/remoteInput/attenuationLaw <inputID:i> <law:i>
```
- 0 = Log, 1 = 1/d²

**Distance Attenuation** (dB/m, for Log law)
```
/remoteInput/distanceAttenuation <inputID:i> <dBperMeter:f>
```
- Range: -6.0 to 0.0

**Distance Ratio** (multiplier, for 1/d² law)
```
/remoteInput/distanceRatio <inputID:i> <ratio:f>
```
- Range: 0.1 to 10.0

**Common Attenuation** (percentage)
```
/remoteInput/commonAttenuation <inputID:i> <percent:f>
```
- Range: 0.0 to 100.0

#### Directivity Group Parameters

**Directivity** (degrees)
```
/remoteInput/directivity <inputID:i> <degrees:f>
```
- Range: 2.0 to 360.0

**Rotation** (degrees)
```
/remoteInput/rotation <inputID:i> <degrees:f>
```
- Range: -179.0 to +180.0

**Tilt** (degrees)
```
/remoteInput/tilt <inputID:i> <degrees:f>
```
- Range: -90.0 to +90.0

**HF Shelf** (dB)
```
/remoteInput/hfShelf <inputID:i> <dB:f>
```
- Range: -24.0 to 0.0

#### Live Source Attenuation Group

**Active** (0 or 1)
```
/remoteInput/liveSourceActive <inputID:i> <state:i>
```
- 0 = OFF, 1 = ON

**Radius** (meters)
```
/remoteInput/liveSourceRadius <inputID:i> <meters:f>
```
- Range: 0.0 to 50.0

**Shape** (0-3)
```
/remoteInput/liveSourceShape <inputID:i> <shape:i>
```
- 0 = linear, 1 = log, 2 = square d², 3 = sine

**Fixed Attenuation** (dB)
```
/remoteInput/liveSourceAttenuation <inputID:i> <dB:f>
```
- Range: -24.0 to 0.0

**Peak Threshold** (dB)
```
/remoteInput/liveSourcePeakThreshold <inputID:i> <dB:f>
```
- Range: -48.0 to 0.0

**Peak Ratio** (ratio)
```
/remoteInput/liveSourcePeakRatio <inputID:i> <ratio:f>
```
- Range: 1.0 to 10.0

**Slow Threshold** (dB)
```
/remoteInput/liveSourceSlowThreshold <inputID:i> <dB:f>
```
- Range: -48.0 to 0.0

**Slow Ratio** (ratio)
```
/remoteInput/liveSourceSlowRatio <inputID:i> <ratio:f>
```
- Range: 1.0 to 10.0

#### Floor Reflections Group

**Active** (0 or 1)
```
/remoteInput/floorReflectionsActive <inputID:i> <state:i>
```
- 0 = OFF, 1 = ON

**Attenuation** (dB)
```
/remoteInput/floorReflectionsAttenuation <inputID:i> <dB:f>
```
- Range: -60.0 to 0.0

**Low Cut Active** (0 or 1)
```
/remoteInput/floorReflectionsLowCutActive <inputID:i> <state:i>
```

**Low Cut Frequency** (Hz)
```
/remoteInput/floorReflectionsLowCutFreq <inputID:i> <hz:f>
```
- Range: 20.0 to 20000.0 (logarithmic)

**High Shelf Active** (0 or 1)
```
/remoteInput/floorReflectionsHighShelfActive <inputID:i> <state:i>
```

**High Shelf Frequency** (Hz)
```
/remoteInput/floorReflectionsHighShelfFreq <inputID:i> <hz:f>
```
- Range: 20.0 to 20000.0 (logarithmic)

**High Shelf Gain** (dB)
```
/remoteInput/floorReflectionsHighShelfGain <inputID:i> <dB:f>
```
- Range: -24.0 to 0.0

**High Shelf Slope**
```
/remoteInput/floorReflectionsHighShelfSlope <inputID:i> <slope:f>
```
- Range: 0.1 to 0.9

**Diffusion** (percentage)
```
/remoteInput/floorReflectionsDiffusion <inputID:i> <percent:f>
```
- Range: 0.0 to 100.0

#### Jitter

**Jitter** (meters)
```
/remoteInput/jitter <inputID:i> <meters:f>
```
- Range: 0.0 to 10.0 (quadratic curve)

#### LFO (Low Frequency Oscillator) Group

**Active** (0 or 1)
```
/remoteInput/lfoActive <inputID:i> <state:i>
```
- 0 = OFF, 1 = ON

**Period** (seconds)
```
/remoteInput/lfoPeriod <inputID:i> <seconds:f>
```
- Range: 0.01 to 100.0 (logarithmic)

**Phase** (degrees)
```
/remoteInput/lfoPhase <inputID:i> <degrees:f>
```
- Range: 0.0 to 360.0

**Shape X/Y/Z** (0-8)
```
/remoteInput/lfoShapeX <inputID:i> <shape:i>
/remoteInput/lfoShapeY <inputID:i> <shape:i>
/remoteInput/lfoShapeZ <inputID:i> <shape:i>
```
- 0 = OFF
- 1 = sine
- 2 = square
- 3 = sawtooth
- 4 = triangle
- 5 = keystone
- 6 = log
- 7 = exp
- 8 = random

**Rate X/Y/Z** (multiplier)
```
/remoteInput/lfoRateX <inputID:i> <rate:f>
/remoteInput/lfoRateY <inputID:i> <rate:f>
/remoteInput/lfoRateZ <inputID:i> <rate:f>
```
- Range: 0.01 to 100.0

**Amplitude X/Y/Z** (meters)
```
/remoteInput/lfoAmplitudeX <inputID:i> <meters:f>
/remoteInput/lfoAmplitudeY <inputID:i> <meters:f>
/remoteInput/lfoAmplitudeZ <inputID:i> <meters:f>
```
- Range: 0.0 to 50.0

**Phase X/Y/Z** (degrees)
```
/remoteInput/lfoPhaseX <inputID:i> <degrees:f>
/remoteInput/lfoPhaseY <inputID:i> <degrees:f>
/remoteInput/lfoPhaseZ <inputID:i> <degrees:f>
```
- Range: 0.0 to 360.0

**Gyrophone** (-1, 0, or 1)
```
/remoteInput/lfoGyrophone <inputID:i> <direction:i>
```
- -1 = Anti-Clockwise
- 0 = OFF
- 1 = Clockwise

### Special Commands

#### Find Device
```
/findDevice [password:s]
```
- Optional password parameter (if configured in settings)
- Triggers screen flash, flashlight, vibration, and alarm
- Works over lock screen
- **Example**: `/findDevice mySecretPass`

---

## Outgoing Messages (App → Server)

Messages the app sends to the WFS-DIY server.

### Marker Position Updates

#### Input Marker Position
```
/marker/positionXY <inputID:i> <x:f> <y:f>
```
- Sent when user drags input markers
- **Example**: `/marker/positionXY 1 6.5 4.2`

#### Cluster Position
```
/cluster/positionXY <clusterID:i> <x:f> <y:f>
```
- Sent when user drags cluster markers
- **Example**: `/cluster/positionXY 1 12.0 9.5`

#### Cluster Height
```
/cluster/positionZ <clusterID:i> <z:f>
```
- Sent when user adjusts cluster height sliders
- **Example**: `/cluster/positionZ 1 5.0`

### Secondary Touch Adjustments

#### Input Map Angular/Radial Changes
```
/marker/angleChange <inputID:i> <parameter:i> <delta:f>
/marker/radialChange <inputID:i> <parameter:i> <delta:f>
```
- **parameter**: Index of selected function (0-31)
- **delta**: Change amount
- Sent during secondary touch gestures

#### Cluster Map Rotation/Scale
```
/cluster/rotation <clusterID:i> <degrees:f>
/cluster/scale <clusterID:i> <factor:f>
```
- **degrees**: Rotation angle (0-360)
- **factor**: Scale multiplier

### Array Adjust Messages

#### Delay/Latency Adjustment
```
/arrayAdjust/delayLatency <arrayID:i> <deltaMs:f>
```
- **arrayID**: Array number (1-5)
- **deltaMs**: Change in milliseconds (±1.0, ±0.1)
- **Example**: `/arrayAdjust/delayLatency 1 0.1`

#### Attenuation Adjustment
```
/arrayAdjust/attenuation <arrayID:i> <deltadB:f>
```
- **deltadB**: Change in dB (±1.0, ±0.1)
- **Example**: `/arrayAdjust/attenuation 2 -0.1`

#### Horizontal Parallax
```
/arrayAdjust/Hparallax <arrayID:i> <deltaMeters:f>
```
- **deltaMeters**: Change in meters (±1.0, ±0.1)
- **Example**: `/arrayAdjust/Hparallax 3 1.0`

#### Vertical Parallax
```
/arrayAdjust/Vparallax <arrayID:i> <deltaMeters:f>
```
- **deltaMeters**: Change in meters (±1.0, ±0.1)
- **Example**: `/arrayAdjust/Vparallax 4 -0.1`

### Input Parameter Updates

All parameter changes are sent using the same format as incoming messages:
```
/remoteInput/<parameter> <inputID:i> <value>
```

See the [Incoming Messages](#input-parameters) section for the complete list of parameters.

### Position Control (Incremental)

These messages are sent by the joystick and Z slider controls:

```
/remoteInput/positionX <inputID:i> <direction:s> <value:f>
/remoteInput/positionY <inputID:i> <direction:s> <value:f>
/remoteInput/positionZ <inputID:i> <direction:s> <value:f>
```

- **direction**: "inc" or "dec"
- **value**: Positive float value for increment/decrement amount
- Allows integration with tracking systems without conflict
- **Example**: `/remoteInput/positionX 1 inc 0.5`

### Request Parameter Update
```
/remoteInput/inputNumber <inputID:i>
```
- Requests server to send all parameters for specified input
- Sent when user taps "Request" button
- **Example**: `/remoteInput/inputNumber 5`

---

## OSC Message Throttling

To prevent network congestion, the app implements message throttling:

- **Rate**: 50Hz (one message every 20ms per parameter)
- **Buffering**: Pending updates are queued if sent too quickly
- **Automatic**: Throttling happens transparently

This ensures smooth operation even during rapid parameter changes (e.g., dragging multiple markers).

---

## Network Setup

### Port Configuration

**Incoming Port** (app receives):
- Default: 8000
- Must match server's outgoing port

**Outgoing Port** (app sends):
- Default: 8001
- Must match server's incoming port

### IP Address

- IPv4 format: xxx.xxx.xxx.xxx
- Server and app must be on same local network
- No NAT or port forwarding required for LAN

### Firewall

Ensure UDP traffic is allowed on configured ports:
```
Protocol: UDP
Incoming Port: 8000 (or configured)
Outgoing Port: 8001 (or configured)
```

---

## Testing OSC Communication

### Using Command Line Tools

**Send test message (Linux/Mac):**
```bash
echo "/inputs 32" | nc -u -w0 <tablet-ip> 8000
```

**Monitor messages (using oscdump):**
```bash
oscdump 8001
```

### OSC Debugging Tools

- **OSCulator** (Mac): https://osculator.net
- **OSC Monitor** (Windows): Various options on GitHub
- **TouchOSC Bridge**: Cross-platform OSC testing

---

## Example OSC Session

**1. Server sends stage configuration:**
```
/stage/width 20.0
/stage/depth 15.0
/stage/height 8.0
/stage/originX 10.0
/stage/originY 7.5
/stage/originZ 0.0
/inputs 16
```

**2. Server sends initial input positions:**
```
/marker/positionXY 1 0.0 0.0
/marker/name 1 "Vocals"
/marker/positionXY 2 2.0 1.0
/marker/name 2 "Guitar"
```

**3. User drags input 1 to new position:**
```
← App sends: /marker/positionXY 1 3.5 2.0
```

**4. User adjusts input 1 attenuation:**
```
← App sends: /remoteInput/attenuation 1 -6.0
```

**5. User moves joystick to adjust position X:**
```
← App sends: /remoteInput/positionX 1 inc 0.5
```

**6. Server sends updated position (from tracking):**
```
→ Server sends: /remoteInput/positionX 1 4.0
```

---

## Notes

- All floating-point values are 32-bit IEEE 754
- Angles are in degrees (0-360 or ±180 as specified)
- Distances are in meters
- Attenuation is in decibels (negative values)
- Boolean values are sent as integers (0 = false, 1 = true)

---

## Support

For questions about OSC implementation:
- GitHub Issues: https://github.com/pob31/WFS_control_2/issues
- Email: contact@pixetbel.org
- WFS-DIY Website: https://wfs-diy.net
