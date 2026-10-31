# Device controls and conversation

Wake with Hey Shoune or Okay Shoune. The foreground service accepts follow-ups until
`stop conversation`, `end conversation`, or `goodbye`. `stop listening` shuts down
the microphone service. Call confirmation still expires independently.

Settings contains Features & commands and Command history. `what can you do` reads
the same product overview. The Features screen distinguishes shipped controls from
browser/Maps workflows that still need manual interaction.

Brightness requires Android's special Modify system settings grant. Media volume
uses the music stream. Radio, location, airplane mode and power saver requests open
public Android settings screens rather than claiming a direct toggle. OEM hotspot
controls are reached through Wireless settings. Drawer controls use accessibility;
close drawer uses Android Back and should be requested while the drawer is open.

Always-active conversations can hear other people and ambient speech. Optional
speaker verification remains applied to each recognized command. Screen-dependent
and private actions still require unlocking. No long-distance/noisy-traffic accuracy
or full third-party automation claim is made.
