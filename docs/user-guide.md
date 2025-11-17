# WFS Control User Guide

**Version 1.0**

Android remote control application for WFS-DIY version 3 wave field synthesis systems.

---

## Table of Contents

1. [Introduction](#introduction)
2. [Installation](#installation)
3. [Getting Started](#getting-started)
4. [Tab Overview](#tab-overview)
5. [Input Map Tab](#input-map-tab)
6. [Lock Input Markers Tab](#lock-input-markers-tab)
7. [View Input Markers Tab](#view-input-markers-tab)
8. [Input Parameters Tab](#input-parameters-tab)
9. [Cluster Map Tab](#cluster-map-tab)
10. [Cluster Height Tab](#cluster-height-tab)
11. [Array Adjust Tab](#array-adjust-tab)
12. [Settings Tab](#settings-tab)
13. [Find Device Feature](#find-device-feature)
14. [Troubleshooting](#troubleshooting)
15. [OSC Communication](#osc-communication)

---

## Introduction

WFS Control is an Android application designed to remotely control WFS-DIY version 3 wave field synthesis audio systems. The app communicates with your WFS audio server using the OSC (Open Sound Control) protocol over your local network.

### Key Features

- **64 Input Channels**: Control up to 64 independent audio sources
- **10 Speaker Clusters**: Configure and position speaker arrays
- **Real-time OSC Communication**: Bi-directional communication with 50Hz update rate
- **Interactive Maps**: Visual positioning of inputs and clusters on a stage grid
- **Comprehensive Parameter Control**: Adjust attenuation, delay, directivity, LFO, and more
- **Find Device**: Locate your tablet in dark venues with screen flash and alarm
- **Responsive Design**: Optimized for both tablets and phones

### System Requirements

- Android 8.0 (API 26) or higher
- Network connection to WFS-DIY server
- Camera permission (for Find Device flashlight feature)

---

## Installation

### Download the APK

1. Visit the [Releases page](https://github.com/pob31/WFS_control_2/releases)
2. Download the latest APK file

### Install on Your Device

1. On your Android device, go to **Settings → Security**
2. Enable **"Install from Unknown Sources"** or **"Install unknown apps"**
3. Open the downloaded APK file
4. Follow the installation prompts
5. When prompted, grant **Camera permission** (required for Find Device feature)

---

## Getting Started

### Initial Setup

1. **Launch the app** - Tap the WFS Control icon
2. **Navigate to Settings tab** (rightmost tab at the top)
3. **Configure network settings**:
   - **Incoming Port**: Port for receiving OSC messages (default: 8000)
   - **Outgoing Port**: Port for sending OSC messages (default: 8001)
   - **IP Address**: IP address of your WFS-DIY server (default: 192.168.1.100)
4. **Tap "Apply Network Settings"** to save

![Settings Tab - Network Configuration](screenshots/settings-network.png)

### Verify Connection

1. Navigate to **Input Parameters tab**
2. Select an input using the dropdown
3. Tap **"Request"** button to fetch current values from server
4. If connected, you should see parameter values populate

### Configure Your Stage

Stage coordinates and dimensions are typically set from the WFS-DIY server and received by the app via OSC. The stage coordinate system includes:

- **Width and Depth**: Stage dimensions in meters
- **Origin Offset**: X, Y, Z coordinates defining the (0,0,0) reference point
- **Grid Lines**: Visual reference at 1-meter intervals

---

## Tab Overview

The app consists of 8 main tabs accessible from the top navigation bar:

| Tab | Purpose |
|-----|---------|
| **Input Map** | Visual positioning of input sources on XY plane |
| **Lock Input Markers** | Lock/unlock markers to prevent accidental movement |
| **View Input Markers** | Show/hide markers on the Input Map |
| **Input Parameters** | Detailed control of all input parameters |
| **Cluster Map** | Position speaker clusters on XY plane |
| **Cluster Height** | Adjust Z-axis (height) of each cluster |
| **Array Adjust** | Quick adjustments for speaker arrays |
| **Settings** | Network configuration and app settings |

![Tab Navigation](screenshots/tabs-overview.png)

---

## Input Map Tab

The Input Map provides an interactive 2D visualization of all input positions on the stage.

![Input Map Tab](screenshots/input-map.png)

### Visual Elements

- **Input Markers**: Numbered circles (1-64) with unique colors
- **Stage Grid**: Coordinate grid with 1-meter intervals
- **Origin Marker**: Shows the (0,0) reference point
- **Corner Labels**: Display stage dimensions and coordinates
- **Marker Labels**: Input names (when assigned)

### Basic Interactions

#### Moving Single Markers

1. **Tap and hold** an input marker
2. **Drag** to new position
3. **Release** to place
4. Position updates are sent to server immediately

#### Moving Multiple Markers (Multi-Touch)

1. **Place multiple fingers** on different markers simultaneously
2. **Drag** each marker to new positions
3. The app supports up to **10 simultaneous touch points**

> **Note**: Locked markers cannot be moved. Hidden markers do not appear on the map.

### Secondary Touch Mode

Secondary touch mode allows you to control parameters by creating a vector between two touch points.

#### How to Use

1. **Place your first finger** on an input marker
2. **Place a second finger** anywhere else on the screen
3. A grey reference line and white active line appear
4. **Rotate** (angular change) or **adjust distance** (radial change) to modify assigned parameters

![Secondary Touch Mode](screenshots/secondary-touch.png)

#### Configuring Secondary Touch Functions

Secondary touch functions are configured in the **Settings tab**:

- **Angular Change Function**: Choose which parameter is controlled by rotating the vector
- **Radial Change Function**: Choose which parameter is controlled by changing vector length

Available functions include:
- Attenuation
- Delay/Latency Compensation
- Height
- Rotation
- Directivity
- LFO controls
- And 25+ more parameters

![Secondary Touch Settings](screenshots/settings-secondary-touch.png)

---

## Lock Input Markers Tab

Use this tab to lock markers, preventing accidental movement in the Input Map.

![Lock Input Markers Tab](screenshots/lock-markers.png)

### Features

- **10 buttons per row** showing inputs 1-64
- Each button displays:
  - Input number
  - Input name (up to 12 characters)
  - Color-coded border matching marker color

### Lock States

- **Locked**: Black background, red text
  - Marker cannot be moved in Input Map
- **Unlocked**: Dark gray background, white text
  - Marker can be freely moved

### How to Use

1. **Tap a button** to toggle lock state
2. Locked markers are immediately protected from movement
3. Lock states are saved and persist between app sessions

---

## View Input Markers Tab

Control which markers are visible on the Input Map.

![View Input Markers Tab](screenshots/view-markers.png)

### Features

Similar layout to Lock Input Markers tab:
- 10 buttons per row
- Input number and name displayed
- Color-coded borders

### Visibility States

- **Visible**: Dark gray background, white text
  - Marker appears on Input Map
- **Hidden**: Black background, blue text
  - Marker does not appear on Input Map

### How to Use

1. **Tap a button** to toggle visibility
2. Hidden markers immediately disappear from Input Map
3. Hidden markers can still be controlled via Input Parameters tab
4. Visibility states are saved between sessions

---

## Input Parameters Tab

The Input Parameters tab provides comprehensive control over all parameters for each input source.

![Input Parameters Tab](screenshots/input-parameters.png)

### Input Selection

At the top of the tab:
- **Input Channel dropdown**: Select which input (1-64) to control
- **Input Name field**: Enter or edit the name (up to 24 characters)
- **Request button**: Fetch current parameter values from server

### Position and Offset Controls

Located at the top of the parameter section:

![Position and Offset Controls](screenshots/position-offset-controls.png)

#### Position X, Y, Z
- **Range**: -50 to +50 meters
- **Controls**:
  - **Number boxes**: Direct value entry with +/- buttons
  - **Joystick**: Controls X and Y simultaneously
  - **Vertical slider**: Controls Z (height)
- **Behavior**: Joystick and Z slider send incremental updates (`inc`/`dec` OSC messages)
  - Allows integration with external tracking systems
  - Direct position updates from tracking won't conflict with manual adjustments

#### Offset X, Y, Z
- **Range**: -50 to +50 meters
- **Controls**: Number boxes with +/- buttons
- **Purpose**: Apply offset values to position for fine-tuning

### Parameter Groups

Parameters are organized into logical groups:

#### Input Group

![Input Parameters](screenshots/input-params-group.png)

**Attenuation** (-92 to 0 dB)
- Vertical slider
- Logarithmic curve for natural volume control
- Default: 0 dB (no attenuation)

**Latency Compensation / Delay** (-100 to +100 ms)
- Horizontal bidirectional slider
- Center position = 0 ms
- Negative values: advance signal
- Positive values: delay signal

**Minimal Latency** (Toggle)
- **Acoustic Precedence**: Standard mode
- **Minimal Latency**: Optimized for low latency

**Cluster Assignment** (Dropdown)
- Assign input to a speaker cluster (1-10)
- Option: "none" for no cluster assignment

**Max Speed Active** (Toggle ON/OFF)
- Enable/disable maximum speed limiting
- When ON, "Max Speed" dial becomes active

**Max Speed** (0.01 to 20 m/s)
- Rotary dial control
- Limits how fast the input can move

**Height Factor** (0 to 100%)
- Rotary dial
- Affects vertical positioning calculation

**Attenuation Law** (Toggle)
- **Log**: Logarithmic distance attenuation
- **1/d²**: Inverse square law
- Selection determines which additional controls appear

**Distance Attenuation** (-6 to 0 dB/m)
- Only visible when Attenuation Law = Log
- Rotary dial control

**Distance Ratio** (0.1 to 10x)
- Only visible when Attenuation Law = 1/d²
- Rotary dial control

**Common Attenuation** (0 to 100%)
- Rotary dial
- Additional attenuation applied to all speakers

#### Directivity Group

![Directivity Parameters](screenshots/directivity-params.png)

**Directivity** (2° to 360°)
- Horizontal bidirectional slider
- Controls the directional spread of the source

**Rotation** (-179° to +180°)
- Direction dial (compass-style)
- Sets the orientation angle

**Tilt** (-90° to +90°)
- Vertical bidirectional slider
- Vertical angle adjustment

**HF Shelf** (-24 to 0 dB)
- Vertical slider
- High-frequency shelf filter

#### Live Source Attenuation Group

![Live Source Attenuation](screenshots/live-source-params.png)

**Active** (Toggle ON/OFF)
- Enables live source attenuation features
- When OFF, all controls in this group are disabled

**Radius** (0 to 50 m)
- Horizontal slider
- Defines the radius of influence

**Shape** (Dropdown)
- Options: linear, log, square d², sine
- Controls attenuation curve shape

**Attenuation** (-24 to 0 dB)
- Vertical slider
- Fixed attenuation amount

**Peak Threshold** (-48 to 0 dB)
- Vertical slider
- Threshold for peak compression

**Peak Ratio** (1 to 10)
- Rotary dial
- Compression ratio for peaks

**Slow Threshold** (-48 to 0 dB)
- Vertical slider
- Threshold for slow compression

**Slow Ratio** (1 to 10)
- Rotary dial
- Compression ratio for slow dynamics

#### Floor Reflections Group

![Floor Reflections](screenshots/floor-reflections-params.png)

**Active** (Toggle ON/OFF)
- Enables floor reflection simulation
- When OFF, all controls disabled

**Attenuation** (-60 to 0 dB)
- Vertical slider
- Attenuation of floor reflections

**Low Cut Active** (Toggle ON/OFF)
- Enables low-cut filter
- When ON, Low Cut Frequency control becomes active

**Low Cut Frequency** (20 to 20,000 Hz)
- Horizontal slider with logarithmic scale
- Sets frequency for low-cut filter

**High Shelf Active** (Toggle ON/OFF)
- Enables high shelf filter
- When ON, related controls become active

**High Shelf Frequency** (20 to 20,000 Hz)
- Horizontal slider with logarithmic scale

**High Shelf Gain** (-24 to 0 dB)
- Vertical slider

**High Shelf Slope** (0.1 to 0.9)
- Horizontal slider
- Controls the steepness of the shelf

**Diffusion** (0 to 100%)
- Rotary dial
- Controls the diffusion amount for reflections

#### Jitter Group

**Jitter** (0 to 10 m)
- Horizontal slider with quadratic curve
- Adds random position variation

#### LFO (Low Frequency Oscillator) Group

![LFO Parameters](screenshots/lfo-params.png)

**Active** (Toggle ON/OFF)
- Enables LFO modulation
- When OFF, all LFO controls are disabled

**Period** (0.01 to 100 seconds)
- Rotary dial with logarithmic scale
- Sets the LFO cycle time

**Phase** (0° to 360°)
- Direction dial
- Sets the starting phase of the LFO

**For each axis (X, Y, Z):**

**Shape X/Y/Z** (Dropdown)
- Options: OFF, sine, square, sawtooth, triangle, keystone, log, exp, random
- When set to anything other than OFF, enables axis controls

**Rate X/Y/Z** (0.01 to 100x)
- Horizontal slider
- Multiplier for the Period value

**Amplitude X/Y/Z** (0 to 50 m)
- Horizontal/Vertical slider
- Maximum displacement for modulation

**Phase X/Y/Z** (0° to 360°)
- Direction dial
- Phase offset for this axis

**Gyrophone** (Dropdown)
- Options: Anti-Clockwise, OFF, Clockwise
- Creates circular motion when enabled

---

## Cluster Map Tab

Position and configure the 10 speaker clusters.

![Cluster Map Tab](screenshots/cluster-map.png)

### Visual Elements

- **10 cluster markers** (arranged in 2 rows of 5)
- Color-coded with unique colors (Cluster 1-10)
- Same stage grid and origin as Input Map

### Basic Interactions

#### Moving Clusters

1. **Tap and hold** a cluster marker
2. **Drag** to new XY position
3. **Release** to place
4. Position updates sent to server immediately

#### Multi-Touch Support

- Control up to **10 clusters simultaneously**
- Each cluster can be moved independently

### Secondary Touch Mode for Clusters

Similar to Input Map, but controls cluster-specific parameters:

#### How to Use

1. **Place first finger** on a cluster marker
2. **Place second finger** elsewhere
3. **Rotate** (angular change) to adjust cluster rotation
4. **Change distance** (radial change) to adjust cluster scale

#### Configuring (Settings Tab)

In Settings → Secondary Touch Functions → Cluster Map:
- **Angular Change**: Toggle ON/OFF for rotation control
- **Radial Change**: Toggle ON/OFF for scale control

![Cluster Secondary Touch](screenshots/cluster-secondary-touch.png)

---

## Cluster Height Tab

Adjust the Z-axis (height) position of each cluster independently.

![Cluster Height Tab](screenshots/cluster-height.png)

### Features

- **10 vertical sliders** (one per cluster)
- Color-coded to match cluster colors
- Labels show cluster number (C 1 through C 10)
- Real-time height display (in meters) while dragging

### How to Use

1. **Drag a slider** up or down
2. Height value displays during adjustment
3. **Range**: 0 to stage height
4. Height is relative to stage origin Z coordinate

---

## Array Adjust Tab

Quick adjustment controls for 5 speaker arrays using a matrix layout.

![Array Adjust Tab](screenshots/array-adjust.png)

### Layout

The tab is organized as a 5×4 grid:
- **5 rows**: One for each array (Array 1-5)
- **4 columns**: One for each adjustment type

### Adjustment Types (Columns)

#### TIME (Blue Column)
Adjust timing/latency for each array:
- **-1.0s**: Decrease by 1 second
- **-0.1s**: Decrease by 0.1 second
- **+0.1s**: Increase by 0.1 second
- **+1.0s**: Increase by 1 second

#### LEVEL (Green Column)
Adjust attenuation for each array:
- **-1.0dB**: Quieter by 1 dB
- **-0.1dB**: Quieter by 0.1 dB
- **+0.1dB**: Louder by 0.1 dB
- **+1.0dB**: Louder by 1 dB

#### HORIZONTAL PARALLAX (Orange Column)
Adjust horizontal position offset:
- **-1.0m**: Bring 1 meter closer
- **-0.1m**: Bring 0.1 meter closer
- **+0.1m**: Send 0.1 meter farther
- **+1.0m**: Send 1 meter farther

#### VERTICAL PARALLAX (Purple Column)
Adjust vertical position offset:
- **-1.0m**: Lower by 1 meter
- **-0.1m**: Lower by 0.1 meter
- **+0.1m**: Raise by 0.1 meter
- **+1.0m**: Raise by 1 meter

### How to Use

1. **Identify the array** you want to adjust (row 1-5)
2. **Choose the parameter type** (column: Time/Level/Horizontal/Vertical)
3. **Tap the appropriate button** for the adjustment amount
4. OSC command is sent immediately
5. Multiple taps accumulate the adjustments

---

## Settings Tab

Configure network connection, secondary touch functions, and app behavior.

![Settings Tab](screenshots/settings.png)

### Network Configuration

![Network Settings](screenshots/settings-network.png)

**Current Device IP**
- Display-only field showing your Android device's local IP address
- Useful for troubleshooting network connectivity

**Incoming Port** (1-65535)
- UDP port for receiving OSC messages from server
- Default: 8000
- Must match the outgoing port configured on your WFS-DIY server

**Outgoing Port** (1-65535)
- UDP port for sending OSC messages to server
- Default: 8001
- Must match the incoming port configured on your WFS-DIY server

**IP Address**
- IPv4 address of your WFS-DIY server
- Format: xxx.xxx.xxx.xxx (e.g., 192.168.1.100)
- Input validation ensures proper format
- Both server and Android device must be on the same network

**Find Device Password** (Optional)
- Password required to trigger `/findDevice` OSC command
- Leave blank to disable password protection
- Security feature to prevent accidental triggering

**Apply Network Settings Button**
- Saves all network configuration changes
- Restarts OSC service with new settings
- Settings persist between app sessions

> **Important**: After changing network settings, tap "Apply Network Settings" to activate the new configuration.

### Secondary Touch Functions

Configure which parameters are controlled by secondary touch gestures.

#### Input Map Functions

![Input Map Secondary Touch](screenshots/settings-input-secondary.png)

**Angular Change Function** (Dropdown)
- Parameter controlled by rotating the vector
- 32 available options

**Radial Change Function** (Dropdown)
- Parameter controlled by changing vector length
- Same 32 options available

**Available Functions:**
- OFF (default)
- Attenuation
- Latency Compensation/Delay
- Height
- Height Factor
- Max Speed
- Distance Attenuation
- Distance Ratio
- Common Attenuation
- Rotation
- Tilt
- Directivity
- HF Shelf
- Live Source: Fixed Attenuation
- Live Source: Radius
- Live Source: Peak Threshold
- Live Source: Peak Ratio
- Live Source: Slow Threshold
- Live Source: Slow Ratio
- Floor Reflections: Attenuation
- Floor Reflections: Diffusion
- Jitter
- LFO Rate X/Y/Z
- LFO Amplitude X/Y/Z
- LFO Phase X/Y/Z
- LFO Period

#### Cluster Map Functions

![Cluster Map Secondary Touch](screenshots/settings-cluster-secondary.png)

**Angular Change** (Toggle ON/OFF)
- When ON: Rotating the vector adjusts cluster rotation angle (0-360°)
- When OFF: Angular changes are ignored

**Radial Change** (Toggle ON/OFF)
- When ON: Changing vector length adjusts cluster scale factor
- When OFF: Radial changes are ignored

### App Control

**Reset App Settings to Defaults**
- Resets number of inputs to 64
- Clears all lock states (all markers unlocked)
- Resets all visibility states (all markers visible)
- Returns all markers to default grid positions
- **Warning**: Shows confirmation dialog before executing
- **Note**: Does not reset network settings

**Shutdown Application**
- Stops the OSC service
- Closes the application completely
- **Warning**: Shows confirmation dialog before executing

---

## Find Device Feature

The Find Device feature helps you locate your tablet in dark venues or performance spaces.

![Find Device Alert](screenshots/find-device-active.png)

### How It Works

The feature can be triggered via OSC command from your WFS-DIY server:

```
/findDevice [password]
```

When activated, the device will:
1. **Screen flash**: White screen flashes 10 times (0.5s on/off cycle)
2. **Camera flashlight**: Strobes in sync with screen (if available)
3. **Vibration**: Pulses with each flash
4. **Alarm sound**: Loud alert tone plays continuously

### Features

- **Works over lock screen**: No need to unlock device
- **High visibility**: Bright white flashes visible in dark environments
- **Audible alert**: Loud sound helps locate device by ear
- **Multi-modal**: Combines visual, audio, and tactile feedback

### Dismissing the Alert

To stop the Find Device alert:
- **Tap the screen** anywhere
- **Press the power button**
- **Press the back button**

### Security

**Password Protection** (Optional):
- Configure in Settings → Network Configuration → Find Device Password
- When set, OSC command must include correct password
- Example: `/findDevice mySecretPassword`
- Prevents accidental or unauthorized triggering

**Permissions Required**:
- **Camera**: Used for flashlight control
- **Show when locked**: Allows alert to work over lock screen

---

## Troubleshooting

### Connection Issues

**App won't connect to server**

1. Verify network settings:
   - Check IP address matches your WFS-DIY server
   - Verify ports match server configuration
   - Ensure device is on same network as server

2. Test connectivity:
   - Navigate to Input Parameters tab
   - Tap "Request" button
   - If no values populate, connection failed

3. Check firewall:
   - Ensure server firewall allows UDP traffic
   - Check incoming/outgoing ports are open

**OSC messages not being received**

1. Verify OSC service is running:
   - Look for service notification in Android status bar

2. Check network configuration:
   - Settings tab should show current device IP
   - Confirm IP is on correct subnet

3. Restart network connection:
   - Modify any network setting
   - Tap "Apply Network Settings"

### Visual Issues

**Markers not visible on map**

1. Check visibility state:
   - Navigate to View Input Markers tab
   - Ensure markers are not hidden (blue text)

2. Verify number of inputs:
   - Check Settings → Current configuration
   - Inactive inputs won't display markers

**Can't move markers**

1. Check lock state:
   - Navigate to Lock Input Markers tab
   - Ensure markers are unlocked (white text)

2. Verify touch is working:
   - Try moving an unlocked marker
   - Ensure single-finger touch is used (not multi-touch)

### Performance Issues

**App running slowly**

1. Close other apps:
   - Free up device memory
   - Reduce background processes

2. Reduce number of active inputs:
   - Fewer markers = better performance

**Screen turns off**

- The app should keep screen on automatically
- If screen dims, check device battery saver settings

### Find Device Not Working

**Flashlight doesn't activate**

1. Check camera permission:
   - Settings → Apps → WFS Control → Permissions
   - Ensure Camera is allowed

2. Verify device has flashlight:
   - Some devices don't have camera flash

**Alert doesn't show over lock screen**

- Check "Show when locked" permission
- May need to grant in device settings

### Reset to Defaults

If issues persist:

1. Navigate to Settings tab
2. Tap "Reset App Settings to Defaults"
3. Confirm in dialog
4. Reconfigure network settings
5. Restart app if needed

---

## OSC Communication

WFS Control communicates with your WFS-DIY server using the OSC (Open Sound Control) protocol over UDP.

### Connection Details

- **Protocol**: OSC over UDP
- **Update Rate**: 50Hz (20ms throttling per parameter)
- **Bi-directional**: App sends and receives messages
- **Message Buffering**: Queues updates during high activity

### Message Format

OSC messages follow this general structure:
```
/address/path <argument1> <argument2> ...
```

Arguments can be:
- **Integer** (i): Whole numbers
- **Float** (f): Decimal numbers
- **String** (s): Text values

### Common Message Categories

For detailed OSC message reference, see [OSC Reference Documentation](osc-reference.md).

**Marker Position Messages:**
- `/marker/positionXY` - Input positions
- `/cluster/positionXY` - Cluster positions
- `/cluster/positionZ` - Cluster heights

**Parameter Messages:**
- `/remoteInput/*` - All input parameters (60+ messages)
- Format: `/remoteInput/parameterName <inputID> <value>`

**Position Control Messages:**
- `/remoteInput/positionX <inputID> inc/dec <value>` - Incremental position updates
- `/remoteInput/positionY <inputID> inc/dec <value>`
- `/remoteInput/positionZ <inputID> inc/dec <value>`

**Offset Messages:**
- `/remoteInput/offsetX <inputID> <value>` - Direct offset values
- `/remoteInput/offsetY <inputID> <value>`
- `/remoteInput/offsetZ <inputID> <value>`

**Configuration Messages:**
- `/inputs <count>` - Number of active inputs (0-64)
- `/stage/width` - Stage width in meters
- `/stage/depth` - Stage depth in meters
- `/stage/height` - Stage height in meters
- `/stage/originX/Y/Z` - Origin offset coordinates

**Special Commands:**
- `/findDevice [password]` - Trigger device locator
- `/remoteInput/inputNumber <inputID>` - Request parameter update

### Network Requirements

- **Same Network**: Android device and server must be on same LAN
- **UDP Ports**: Ensure firewall allows UDP traffic on configured ports
- **No Router NAT**: OSC communication works best on local network without NAT

### Debugging OSC

To verify OSC communication:

1. Use an OSC monitor tool on your computer
2. Listen on the configured ports
3. Send test messages from the app
4. Verify messages are received and formatted correctly

---

## About

**WFS Control v1.0**

Copyright © 2025 Pierre-Olivier Boulant

This application is licensed under the GNU General Public License v3.0.

For more information:
- Website: https://wfs-diy.net
- GitHub: https://github.com/pob31/WFS_control_2
- Contact: contact@pixetbel.org

### Credits

Designed for WFS-DIY version 3 wave field synthesis systems.

### License

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

See the [LICENSE](../LICENSE) file for full details.

---

**Note:** This user guide refers to screenshot images that need to be provided. Screenshots should be placed in the `docs/screenshots/` directory with the filenames referenced in this document.
