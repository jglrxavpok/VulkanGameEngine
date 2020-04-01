#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(location = 0) in vec2 fragPos;
layout(location = 1) in vec2 fragCoords;

layout(input_attachment_index = 0, binding = 0) uniform subpassInput lightingOut;
layout(input_attachment_index = 1, binding = 1) uniform subpassInput ssaoFactor;
layout(location = 0) out vec4 outColor;

void main() {
    vec4 fragmentColor = subpassLoad(lightingOut);
    float fragmentSSAOOcclusion = subpassLoad(ssaoFactor).r;
    vec3 color = fragmentColor.rgb * fragmentSSAOOcclusion;
    outColor = vec4(color, 1.0);
}