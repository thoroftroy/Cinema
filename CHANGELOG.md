# Cinema changelog

## v0.5.24

- Fixed a gap above the keyboard in screens with a bottom tab bar: the tab bar now steps aside while you type, so you can see what you are writing

## v0.5.23

- Tapping Record on the Wonderdesk Launcher's floating button starts clip mode at once, and Start clip, Save clip and Stop appear on the button as a card as well as in the notification

## v0.5.22

- Cinema now tells the Wonderdesk Launcher's floating button when it is recording or in clip mode, and accepts Start clip, Save clip and Stop from it. The button draws nothing while recording, so it never appears in a recording

## v0.5.21

- Screen recording runs much cooler and lighter: a still screen is no longer re-encoded at the full frame rate, the encoder uses steady-quality mode at relaxed priority, normal recordings need fewer full frames, and a sound-capture loop that could spin the processor now rests. New installs start at 80% size and 6 Mbps (change them in the Record tab)
- Fixed the video controls never coming back when you tap a playing video
- Screen recording can be started and stopped from the Wonderdesk floating button

## v0.5.20

- Fixed: swiping sideways in the player now really goes to the next or previous video in your library. The pinch-to-zoom gesture was swallowing every one-finger swipe, which also stopped swipe-to-seek from working
- The previous and next buttons beside play follow the same order

## v0.5.19

- Swipe sideways in the player to go to the next or previous video in your library (newest first), or use the new previous and next buttons beside play. A setting switches swiping back to seeking
- The player's options sheet and every video's menu in the library now have Delete
- Screen recordings and clips appear in the library at once, newest first
- Cast to a TV and the Quick start note are gone from the Record tab

- Screen recording and clip mode now announce themselves: a pop-up appears when recording starts, and a notification stays in the shade with a running clock and Stop, Clip and Turn off buttons. Tapping it opens the Record tab
- The Record tab shows a live red indicator with the time while recording, and a "To record properly" checklist with a button for each missing permission (notifications, microphone, phone sound)
- If notifications are off or blocked, recording says so before it starts and offers to fix it or record anyway, instead of silently showing only Android's own "sharing your screen" chip

## v0.5.18

- Streaming rebuilt: a bigger buffer ahead of the picture (about 25 to 90 seconds from far away), video already fetched is kept on disk so rewinding and replaying cost nothing, and the player measures the connection
- Opening and buffering now say what is happening: a loading card while the first seconds arrive, a Buffering card with how much is ready and how fast the source is sending compared with what the video needs, and a warning when no data is arriving
- A buffered bar and a seconds-buffered readout show under the seek bar for videos from a computer or the network

## v0.5.17

- New setting: the size the editor saves videos at

## v0.5.16

- New setting: turn the screen sideways by itself for wide videos

## v0.5.15

- New subtitle settings: text size, and a dark box or outlined white text

## v0.5.14

- Text size and accent colour chosen in Control Room now apply here
- New Rotate with the phone switch in Settings. Rotates by default

## v0.5.13

- New playback settings: skip distance, starting speed, picture fit, control hide delay, keep screen on, repeat, and a switch for every touch gesture
- Hide short videos from the library

## v0.5.12

- The editor is now a real multi-clip timeline: add videos, split, cut, reorder and remove parts, and save them as one movie, even when the videos are different shapes
- Cast a video to any DLNA smart TV, game console or media box with no Google services: pick the TV, then pause, seek and stop from the phone
- Mirror the whole screen is still one tap away from the cast window

## v0.5.11

- Screen recorder: record the whole screen from the new Record tab or a quick settings tile, with quality, frame rate and sound choices
- Clip mode: it watches the screen without saving anything, and Clip keeps the last few seconds and carries on recording until you stop
- Editor: auto enhance, brightness, contrast, colour, warmth, zoom and crop, a caption, speed, volume and music or a voiceover from Echo, all shown live
- Cast this screen to a TV from the Record tab
- Loading screens now say what they are doing

## v0.5.10

- Widgets show a realistic preview in the widget list, so you can see what you're adding
- Fixed a crash when moving around with arrow keys or Tab while a screen was closing

## v0.5.9

- Open a network stream (the cast icon in the toolbar): paste any video link, including live HLS (.m3u8), DASH (.mpd) and RTSP streams; a link you copied is filled in for you
- Browsers and other apps can open video links straight in Cinema

## v0.5.8

- The Continue watching widget wakes the phone far less often; it refreshes itself when you watch something
- Lighter on the battery: in the Super look the drifting sparkles settle after a few seconds without a touch (the next tap wakes them), so a screen you're reading no longer redraws 25 times a second

## v0.5.7

- Changing the look in Control Room now restyles this app's widgets straight away, with no refresh or restart

## v0.5.6

- Widgets: choose the title colour in each widget's settings (automatic, white, dark, Aqua blue, Super pink or gold), on top of the background looks

## v0.5.5

- Widgets can now match your look: new Bliss blue and Super neon backgrounds, and “Match my Wonderdesk look” follows whatever Control Room is set to

## v0.5.4

- Plays the next video in the same folder when one ends, in name order, so episodes run one after another (Settings › Playback; off for loops and the sleep timer)
- New setting to turn off resuming where you left off
- Fixed the Continue watching widget not refreshing
- Fixes from a full round of stress testing on every screen

## v0.5.3

- Sleep timer in the ⋮ menu: pause after 15 to 90 minutes, or at the end of the video
- A-B repeat: mark the start and end of a part and it plays on a loop
- Memories can now send a video straight to Cinema's editor
- Player controls now come from the suite kit, so Memories and Wayfinder look the same
- Tap the tab you're already on to glide back to the top of its list
- New Continue watching widget: the video you were part-way through with a picture, how far you got and a Resume button
- Nothing required stays hidden: if Android is blocking something the app needs, a slim banner says what and takes you straight to the switch, and Settings › Permissions and access lists everything with its state

## v0.5.2

- Smoother everywhere: new screens, tabs and Settings ease in instead of snapping, and buttons, tabs, chips and switches give a light tap you can feel (following your phone's touch-feedback setting)
- Android's predictive back: the back gesture shows a live preview of where you're going
- Super look: tap sparkles now match Bliss Player: soft glowing sparks with trails, drifting rainbow colours and a faint aurora, instead of big solid circles

## v0.5.1

- Much faster and smoother: the app is now built optimised (the way Play Store apps are), so screens open quicker, scrolling is fluid and the APK is about a quarter of the size
- Screens and lists no longer decode pictures on the main thread, and skip redrawing parts that didn't change

## v0.5.0

- Polish and stability fixes from the suite-wide test pass
- Works properly in landscape: tabs move to a rail at the side so the content keeps its height, and screens that ran out of room now scroll
- If the app ever crashes it says so next time it opens and offers to send a report (only the error, app version and phone model, with file names and links removed). Turn on automatic sending in Control Room › Wonderdesk
- Lists can no longer crash when two items share a name

## v0.4.0

- Cut parts out of the middle of a video: split the timeline into segments, keep or cut each one, undo, and export just the parts you kept
- If the app ever crashes or freezes, a report goes to Control Room so it can be shared and fixed
- App icon art is larger and fills its tile without being cut off

## v0.3.1

- New fullscreen player look that follows your suite theme: a floating QuickTime-style glass controller with a glossy gel play button and an iTunes-style scrubber (Luna blue in Bliss, graphite in Dark, neon in Super)
- The player checks the position less often while the controls are hidden, saving battery during long videos
- App icon redrawn so nothing is cut off: round icons fill the space, the rest sit on an Aqua tile
- Smoother and lighter on the battery: the window background no longer animates, tap effects sleep until you tap, and nothing ticks while the app is in the background
- Opening the app no longer waits on Control Room to read the suite look

## v0.3.0

- Every video on the phone in a library with folders and Continue watching
- Fullscreen gesture player with audio and subtitle tracks, speed, lock, loop and picture-in-picture
- Video editor: trim, mute, rotate and resize
