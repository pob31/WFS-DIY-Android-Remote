# WFS Control Quick Start Guide

Get up and running with WFS Control in 5 minutes.

## 1. Installation

1. Download the latest APK from [GitHub Releases](https://github.com/pob31/WFS_control_2/releases)
2. Enable "Install from Unknown Sources" in your Android device settings
3. Install the APK
4. Grant Camera permission when prompted

## 2. Network Setup

1. Launch WFS Control
2. Navigate to **Settings** tab (rightmost tab)
3. Configure network:
   - **Incoming Port**: 8000 (or your server's outgoing port)
   - **Outgoing Port**: 8001 (or your server's incoming port)
   - **IP Address**: Your WFS-DIY server IP (e.g., 192.168.1.100)
4. Tap **"Apply Network Settings"**

## 3. Verify Connection

1. Go to **Input Parameters** tab
2. Select Input 1 from dropdown
3. Tap **"Request"** button
4. If values populate, you're connected!

## 4. Basic Controls

### Move Input Markers
1. Go to **Input Map** tab
2. Drag markers to position inputs on stage

### Adjust Input Parameters
1. Go to **Input Parameters** tab
2. Select an input
3. Adjust sliders and dials
4. Changes send to server automatically

### Position Clusters
1. Go to **Cluster Map** tab
2. Drag cluster markers to position speaker arrays
3. Use **Cluster Height** tab to adjust vertical position

## 5. Common Tasks

**Lock a marker** (prevent accidental movement):
- Go to **Lock Input Markers** tab → Tap marker button

**Hide a marker** (clean up view):
- Go to **View Input Markers** tab → Tap marker button

**Quick array adjustments**:
- Go to **Array Adjust** tab → Tap adjustment buttons

**Find your tablet in the dark**:
- Send OSC: `/findDevice` from server
- Screen flashes, flashlight strobes, alarm sounds

## Need Help?

See the [Full User Guide](user-guide.md) for detailed documentation.

## Support

- Report issues: https://github.com/pob31/WFS_control_2/issues
- Contact: contact@pixetbel.org
- Website: https://wfs-diy.net
