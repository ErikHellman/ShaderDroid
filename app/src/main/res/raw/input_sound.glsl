// Created by inigo quilez - iq/2013
// License Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License.
uniform vec3      iResolution;           // viewport resolution (in pixels)
uniform float     iGlobalTime;           // shader playback time (in seconds)
uniform vec4      iMouse;                // mouse pixel coords. xy: current (if MLB down), zw: click
uniform sampler2D iChannel0;             // input channel. XX = 2D/Cube

void main(void)
{
     vec4 mouse = iMouse;
     float globalTime = iGlobalTime;
    // create pixel coordinates
	vec2 uv = gl_FragCoord.xy / iResolution.xy;

	// first texture row is frequency data
	float fft  = texture2D( iChannel0, vec2(uv.x,0.25) ).x;

    // second texture row is the sound wave
	float wave = texture2D( iChannel0, vec2(uv.x,0.75) ).x;

	// convert frequency to colors
	vec3 col = vec3( fft, 4.0*fft*(1.0-fft), 1.0-fft ) * fft;

    // add wave form on top
	col += 1.0 -  smoothstep( 0.0, 0.15, abs(wave - uv.y) );

	// output final color
	gl_FragColor = vec4(col,1.0);
}