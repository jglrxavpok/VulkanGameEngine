#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(binding=0) uniform CameraObject {
    mat4 view;
    mat4 proj;
} camera;

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inColor;
layout(location = 2) in vec2 inTexCoords;
layout(location = 3) in vec3 inNormal;

layout(location = 4) in mat4 model;

layout(location = 0) out vec3 fragColor;
layout(location = 1) out vec2 fragTexCoord;
layout(location = 2) out vec3 fragWorldPos;
layout(location = 3) out vec3 fragViewNormal;
layout(location = 4) out vec3 fragViewPos;

void main() {
    mat4 modelview = camera.view * model;
    vec4 worldPos = model * vec4(inPosition, 1.0);
    fragColor = inColor;
    fragTexCoord = inTexCoords;
    fragWorldPos = worldPos.xyz;
    fragViewNormal = normalize(transpose(inverse(mat3(modelview))) * inNormal);
    fragViewPos = (camera.view * worldPos).xyz;

    gl_Position = camera.proj * vec4(fragViewPos, 1.0);
}