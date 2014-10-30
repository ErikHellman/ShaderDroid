// by nikos papadopoulos, 4rknova / 2013
// Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License.

#ifdef GL_ES
precision highp float;
#endif

uniform vec3      iResolution;           // viewport resolution (in pixels)
uniform float     iGlobalTime;           // shader playback time (in seconds)
uniform vec4      iMouse;                // mouse pixel coords. xy: current (if MLB down), zw: click
uniform sampler2D iChannel0;             // input channel. XX = 2D/Cube

#define PI	3.14159265359

float s(vec2 c) {return texture2D(iChannel0, c).x;}
vec3 fft = vec3(s(vec2(.0,.25)),s(vec2(.5,.25)),s(vec2(1.,.25)));
vec3 wav = vec3(s(vec2(.0,.75)),s(vec2(.5,.75)),s(vec2(1.,.75)));
float t  = cos(fft.x * 2. / PI);
float ct = cos(t);
float st = sin(t);

float hash(in vec3 p)
{
	return fract(sin(dot(p,vec3(283.6,127.1,311.7))) * 43758.5453);
}

float noise(in vec3 p){
	p.y -= iGlobalTime * 2. + 2. * fft.x * fft.y;
	p.z += iGlobalTime * .4 - fft.z;
	p.x += 2. * cos(wav.y);

    vec3 i = floor(p);
	vec3 f = fract(p);
	f *= f * (3.-2.*f);

    return mix(
		mix(mix(hash(i + vec3(0.,0.,0.)), hash(i + vec3(1.,0.,0.)),f.x),
			mix(hash(i + vec3(0.,1.,0.)), hash(i + vec3(1.,1.,0.)),f.x),
			f.y),
		mix(mix(hash(i + vec3(0.,0.,1.)), hash(i + vec3(1.,0.,1.)),f.x),
			mix(hash(i + vec3(0.,1.,1.)), hash(i + vec3(1.,1.,1.)),f.x),
			f.y),
		f.z);
}

float fbm(in vec3 p)
{
	return .5000 * noise(1. * p)
		 + .2500 * noise(2. * p)
	     + .1250 * noise(4. * p)
	     + .0625 * noise(8. * p);
}

void main(void)
{
    vec4 mouse = iMouse;
	vec2 uv = gl_FragCoord.xy / iResolution.xy;
	vec2 vc = (2. * uv - 1.) * vec2(iResolution.x / iResolution.y, 1.);

	vc = vec2(vc.x * ct - vc.y * st
			 ,vc.y * ct + vc.x * st);

	vec3 rd = normalize(vec3(.5, vc.x, vc.y));
	vec3 c = 2. * vec3(fbm(rd)) * fft.xyz;
	c += hash(hash(uv.xyy) * uv.xyx * iGlobalTime) * .2;;
	c *= .9 * smoothstep(length(uv * .5 - .25), .7, .4);

	gl_FragColor = vec4(c,1.);
}