#!/usr/bin/env bash
set -euo pipefail

# Builds an original 24-second VeCTR teaser from the supplied product screens.
# The visual language is intentionally VeCTR: graphite, ember red, and amber.
# It does not reuse third-party logos, footage, or copy from the visual reference.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_DIR="/home/neelshah/axon/files/incoming"
OUT_DIR="$ROOT_DIR/marketing/output"
WORK_DIR="$OUT_DIR/.work"
FONT="/usr/share/fonts/noto/NotoSans-Regular.ttf"
MONO_FONT="/usr/share/fonts/noto/NotoSansMono-Regular.ttf"

mkdir -p "$WORK_DIR"
rm -f "$WORK_DIR"/*.mp4 "$OUT_DIR/Vectr-intro-teaser.mp4"

render_title() {
  ffmpeg -y -f lavfi -i "color=c=0x09090d:s=1920x1080:d=4:r=30" \
    -filter_complex "
      [0:v]format=rgba,
      drawbox=x=0:y=0:w=1920:h=1080:color=0x13060a@0.9:t=fill,
      drawbox=x=-300+150*t:y=745:w=1120:h=5:color=0xff4b57@0.85:t=fill,
      drawbox=x=1200-110*t:y=275:w=720:h=4:color=0xffb36b@0.55:t=fill,
      drawtext=fontfile=${FONT}:text='VE':fontcolor=0xf5f1ea:fontsize=172:x=(w-text_w)/2-200:y=380:alpha='if(lt(t,0.55),0,(t-0.55)/0.45)',
      drawtext=fontfile=${FONT}:text='CTR':fontcolor=0xf5f1ea:fontsize=172:x=(w-text_w)/2+80:y=380:alpha='if(lt(t,0.85),0,(t-0.85)/0.45)',
      drawtext=fontfile=${FONT}:text='•':fontcolor=0xff4b57:fontsize=180:x=(w-text_w)/2-35:y=382:alpha='if(lt(t,1.1),0,(t-1.1)/0.45)',
      drawtext=fontfile=${MONO_FONT}:text='YOUR LOCAL DEVICE CONSOLE':fontcolor=0xb5a69b:fontsize=30:x=(w-text_w)/2:y=600:alpha='if(lt(t,1.55),0,(t-1.55)/0.45)',
      drawtext=fontfile=${MONO_FONT}:text='PHONE + DESKTOP. ONE PRIVATE SPACE.':fontcolor=0xff6a57:fontsize=20:x=(w-text_w)/2:y=680:alpha='if(lt(t,2.1),0,(t-2.1)/0.45)'" \
    -c:v libx264 -pix_fmt yuv420p -crf 17 -movflags +faststart "$WORK_DIR/01-title.mp4"
}

render_home() {
  ffmpeg -y -loop 1 -i "$SOURCE_DIR/Screenshot_20260721_200123.jpg" \
    -f lavfi -i "color=c=0x09090d:s=1920x1080:d=5:r=30" \
    -filter_complex "
      [1:v]format=rgba,drawbox=x=0:y=0:w=1920:h=1080:color=0x21070d@0.9:t=fill,
      drawbox=x=360:y=100:w=1200:h=880:color=0xff3e4d@0.12:t=fill[bg];
      [0:v]scale=420:-1,format=rgba,fade=t=in:st=0:d=0.45,fade=t=out:st=4.55:d=0.45[phone];
      [bg][phone]overlay=x='750+16*sin(t*1.3)':y='86+10*sin(t*1.1)',
      drawtext=fontfile=${MONO_FONT}:text='CONTROL, WITHOUT THE CLUTTER':fontcolor=0xf5f1ea:fontsize=42:x=110:y=155:alpha='if(lt(t,0.65),0,(t-0.65)/0.35)',
      drawtext=fontfile=${FONT}:text='A private bridge between your phone and your computer.':fontcolor=0xb5a69b:fontsize=27:x=110:y=220:alpha='if(lt(t,1),0,(t-1)/0.4)',
      drawbox=x=110:y=300:w=440:h=2:color=0xff4b57@0.8:t=fill,
      drawtext=fontfile=${MONO_FONT}:text='CAPTURE  •  FILES  •  FOCUS':fontcolor=0xff6a57:fontsize=22:x=110:y=345" \
    -t 5 -r 30 -c:v libx264 -pix_fmt yuv420p -crf 17 "$WORK_DIR/02-home.mp4"
}

render_control() {
  ffmpeg -y -loop 1 -i "$SOURCE_DIR/Screenshot_20260721_200148.jpg" \
    -loop 1 -i "$SOURCE_DIR/Screenshot_20260721_200154.jpg" \
    -f lavfi -i "color=c=0x0a0a0f:s=1920x1080:d=5:r=30" \
    -filter_complex "
      [2:v]format=rgba,drawbox=x=0:y=0:w=1920:h=1080:color=0x101016@1:t=fill[bg];
      [0:v]scale=880:-1,format=rgba,fade=t=in:st=0:d=0.4,fade=t=out:st=4.55:d=0.45[macro];
      [1:v]scale=650:-1,format=rgba,fade=t=in:st=0.5:d=0.45,fade=t=out:st=4.55:d=0.45[touch];
      [bg][macro]overlay=x='-20+20*sin(t*0.9)':y=252[a];
      [a][touch]overlay=x='1270+14*sin(t*1.2)':y=250,
      drawtext=fontfile=${MONO_FONT}:text='YOUR DESKTOP, WITHIN REACH':fontcolor=0xf5f1ea:fontsize=44:x=(w-text_w)/2:y=92,
      drawtext=fontfile=${FONT}:text='Macros that move. A touchpad that responds.':fontcolor=0xb5a69b:fontsize=27:x=(w-text_w)/2:y=160,
      drawbox=x=896:y=250:w=2:h=580:color=0xff4b57@0.7:t=fill" \
    -t 5 -r 30 -c:v libx264 -pix_fmt yuv420p -crf 17 "$WORK_DIR/03-control.mp4"
}

render_flow() {
  ffmpeg -y -loop 1 -i "$SOURCE_DIR/Screenshot_20260721_200200.jpg" \
    -loop 1 -i "$SOURCE_DIR/Screenshot_20260721_200142.jpg" \
    -loop 1 -i "$SOURCE_DIR/Screenshot_20260721_201136.jpg" \
    -f lavfi -i "color=c=0x09090d:s=1920x1080:d=5:r=30" \
    -filter_complex "
      [3:v]format=rgba,drawbox=x=0:y=0:w=1920:h=1080:color=0x16080d@1:t=fill[bg];
      [0:v]scale=330:-1,format=rgba,fade=t=in:st=0:d=0.4,fade=t=out:st=4.6:d=0.4[files];
      [1:v]scale=330:-1,format=rgba,fade=t=in:st=0.25:d=0.4,fade=t=out:st=4.6:d=0.4[capture];
      [2:v]scale=330:-1,format=rgba,fade=t=in:st=0.5:d=0.4,fade=t=out:st=4.6:d=0.4[inventory];
      [bg][files]overlay=x=270:y='190+12*sin(t*1.2)'[a];
      [a][capture]overlay=x=795:y='150+12*sin(t*1.3)'[b];
      [b][inventory]overlay=x=1320:y='190+12*sin(t*1.1)',
      drawtext=fontfile=${MONO_FONT}:text='KEEP THE EVERYDAY MOVING':fontcolor=0xf5f1ea:fontsize=44:x=(w-text_w)/2:y=85,
      drawtext=fontfile=${FONT}:text='Files, notes, and the things you need to remember.':fontcolor=0xb5a69b:fontsize=27:x=(w-text_w)/2:y=995,
      drawbox=x=590:y=515:w=250:h=3:color=0xffb36b@0.9:t=fill,
      drawbox=x=1090:y=515:w=250:h=3:color=0xff4b57@0.9:t=fill" \
    -t 5 -r 30 -c:v libx264 -pix_fmt yuv420p -crf 17 "$WORK_DIR/04-flow.mp4"
}

render_focus() {
  ffmpeg -y -loop 1 -i "$SOURCE_DIR/Screenshot_20260721_200803.jpg" \
    -loop 1 -i "$SOURCE_DIR/Screenshot_20260721_200818.jpg" \
    -f lavfi -i "color=c=0x09090d:s=1920x1080:d=4:r=30" \
    -filter_complex "
      [2:v]format=rgba,drawbox=x=0:y=0:w=1920:h=1080:color=0x0f1118@1:t=fill[bg];
      [0:v]scale=365:-1,format=rgba,fade=t=in:st=0:d=0.4,fade=t=out:st=3.55:d=0.45[focus];
      [1:v]scale=365:-1,format=rgba,fade=t=in:st=0.3:d=0.4,fade=t=out:st=3.55:d=0.45[bounty];
      [bg][focus]overlay=x='470+14*sin(t*1.15)':y=152[a];
      [a][bounty]overlay=x='1085+14*sin(t*1.05)':y=152,
      drawtext=fontfile=${MONO_FONT}:text='MAKE ROOM FOR WHAT MATTERS':fontcolor=0xf5f1ea:fontsize=44:x=(w-text_w)/2:y=82,
      drawtext=fontfile=${FONT}:text='Focus with intention. See the progress.':fontcolor=0xb5a69b:fontsize=27:x=(w-text_w)/2:y=1000,
      drawbox=x=530:y=865:w=860:h=3:color=0xff4b57@0.9:t=fill" \
    -t 4 -r 30 -c:v libx264 -pix_fmt yuv420p -crf 17 "$WORK_DIR/05-focus.mp4"
}

render_end() {
  ffmpeg -y -f lavfi -i "color=c=0x09090d:s=1920x1080:d=4:r=30" \
    -filter_complex "
      [0:v]format=rgba,drawbox=x=0:y=0:w=1920:h=1080:color=0x11080c@1:t=fill,
      drawbox=x=0:y=780:w=1920:h=5:color=0xff4b57@0.85:t=fill,
      drawtext=fontfile=${FONT}:text='VE':fontcolor=0xf5f1ea:fontsize=166:x=(w-text_w)/2-190:y=310:alpha='if(lt(t,0.35),0,(t-0.35)/0.45)',
      drawtext=fontfile=${FONT}:text='CTR':fontcolor=0xf5f1ea:fontsize=166:x=(w-text_w)/2+82:y=310:alpha='if(lt(t,0.6),0,(t-0.6)/0.45)',
      drawtext=fontfile=${FONT}:text='•':fontcolor=0xff4b57:fontsize=175:x=(w-text_w)/2-33:y=312:alpha='if(lt(t,0.85),0,(t-0.85)/0.45)',
      drawtext=fontfile=${MONO_FONT}:text='YOUR LOCAL DEVICE CONSOLE':fontcolor=0xb5a69b:fontsize=28:x=(w-text_w)/2:y=520:alpha='if(lt(t,1.25),0,(t-1.25)/0.4)',
      drawtext=fontfile=${MONO_FONT}:text='PRIVATE BY DEFAULT':fontcolor=0xff6a57:fontsize=22:x=(w-text_w)/2:y=625:alpha='if(lt(t,1.7),0,(t-1.7)/0.4)'" \
    -c:v libx264 -pix_fmt yuv420p -crf 17 -movflags +faststart "$WORK_DIR/06-end.mp4"
}

render_title
render_home
render_control
render_flow
render_focus
render_end

ffmpeg -y \
  -i "$WORK_DIR/01-title.mp4" -i "$WORK_DIR/02-home.mp4" -i "$WORK_DIR/03-control.mp4" \
  -i "$WORK_DIR/04-flow.mp4" -i "$WORK_DIR/05-focus.mp4" -i "$WORK_DIR/06-end.mp4" \
  -filter_complex "[0:v][1:v]xfade=transition=fade:duration=0.55:offset=3.45[a];[a][2:v]xfade=transition=fade:duration=0.55:offset=7.9[b];[b][3:v]xfade=transition=fade:duration=0.55:offset=12.35[c];[c][4:v]xfade=transition=fade:duration=0.55:offset=16.8[d];[d][5:v]xfade=transition=fade:duration=0.55:offset=20.25,format=yuv420p[v]" \
  -map "[v]" -c:v libx264 -crf 18 -movflags +faststart "$OUT_DIR/Vectr-intro-teaser.mp4"

echo "Created: $OUT_DIR/Vectr-intro-teaser.mp4"
