import baseInfo from "./base-info";

function hash3d() {
    // This function is based on this article: https://bl.ocks.org/camargo/649e5903c4584a21a568972d4a2c16d3
    // The 'mat4' object is created from code in the gl-matrix library: https://github.com/toji/gl-matrix
    var gl, program, canvas;

    // The non-minified versions of these shaders are available in
    // WebSite/partials/fragment-shader.glsl and
    // WebSite/partials/vertex-shader.glsl
    var VERTEX_SHADER = 'attribute vec3 c,d; uniform vec4 e; uniform vec3 f,g;uniform mat4 h,i;varying vec3 j;void main(){vec3 a=normalize(d);vec4 b=h*vec4(c,1.);vec3 k=normalize(vec3(e-b));j=g*f*max(dot(k,a),0.),gl_Position=i*vec4(c,1.);}';
    var FRAGMENT_SHADER = '#ifdef GL_ES\n' +
        'precision mediump float;\n' +
        '#endif\n' +
        'varying vec3 j;void main(){gl_FragColor = vec4(j, 1.0);}';

    // This object uses code from the gl-matrix library: https://github.com/toji/gl-matrix
    var mat4 = {
        create: function () {
            var result = new Array(16);
            for (var i = 0; i < 16; i++) {
                result[i] = (i % 5 == 0 ? 1 : 0);
            }
            return result;
        },
        perspective: function (out, fovy, aspect, near, far) {
            var f = 1.0 / Math.tan(fovy / 2), nf;
            out[0] = f / aspect;
            out[1] = 0;
            out[2] = 0;
            out[3] = 0;
            out[4] = 0;
            out[5] = f;
            out[6] = 0;
            out[7] = 0;
            out[8] = 0;
            out[9] = 0;
            out[11] = -1;
            out[12] = 0;
            out[13] = 0;
            out[15] = 0;
            if (far != null && far !== Infinity) {
                nf = 1 / (near - far);
                out[10] = (far + near) * nf;
                out[14] = (2 * far * near) * nf;
            } else {
                out[10] = -1;
                out[14] = -2 * near;
            }
            return out;
        },
        lookAt: function (out, eye, center, up) {
            var x0, x1, x2, y0, y1, y2, z0, z1, z2, len;
            var eyex = eye[0];
            var eyey = eye[1];
            var eyez = eye[2];
            var upx = up[0];
            var upy = up[1];
            var upz = up[2];
            var centerx = center[0];
            var centery = center[1];
            var centerz = center[2];

            if (Math.abs(eyex - centerx) < 0.000001 &&
                Math.abs(eyey - centery) < 0.000001 &&
                Math.abs(eyez - centerz) < 0.000001) {
                return mat4.identity(out);
            }

            z0 = eyex - centerx;
            z1 = eyey - centery;
            z2 = eyez - centerz;

            len = 1 / Math.hypot(z0, z1, z2);
            z0 *= len;
            z1 *= len;
            z2 *= len;

            x0 = upy * z2 - upz * z1;
            x1 = upz * z0 - upx * z2;
            x2 = upx * z1 - upy * z0;
            len = Math.hypot(x0, x1, x2);
            if (!len) {
                x0 = 0;
                x1 = 0;
                x2 = 0;
            } else {
                len = 1 / len;
                x0 *= len;
                x1 *= len;
                x2 *= len;
            }

            y0 = z1 * x2 - z2 * x1;
            y1 = z2 * x0 - z0 * x2;
            y2 = z0 * x1 - z1 * x0;

            len = Math.hypot(y0, y1, y2);
            if (!len) {
                y0 = 0;
                y1 = 0;
                y2 = 0;
            } else {
                len = 1 / len;
                y0 *= len;
                y1 *= len;
                y2 *= len;
            }

            out[0] = x0;
            out[1] = y0;
            out[2] = z0;
            out[3] = 0;
            out[4] = x1;
            out[5] = y1;
            out[6] = z1;
            out[7] = 0;
            out[8] = x2;
            out[9] = y2;
            out[10] = z2;
            out[11] = 0;
            out[12] = -(x0 * eyex + x1 * eyey + x2 * eyez);
            out[13] = -(y0 * eyex + y1 * eyey + y2 * eyez);
            out[14] = -(z0 * eyex + z1 * eyey + z2 * eyez);
            out[15] = 1;

            return out;
        },
        multiply: function (out, a, b) {
            var a00 = a[0], a01 = a[1], a02 = a[2], a03 = a[3];
            var a10 = a[4], a11 = a[5], a12 = a[6], a13 = a[7];
            var a20 = a[8], a21 = a[9], a22 = a[10], a23 = a[11];
            var a30 = a[12], a31 = a[13], a32 = a[14], a33 = a[15];

            var b0 = b[0], b1 = b[1], b2 = b[2], b3 = b[3];
            out[0] = b0 * a00 + b1 * a10 + b2 * a20 + b3 * a30;
            out[1] = b0 * a01 + b1 * a11 + b2 * a21 + b3 * a31;
            out[2] = b0 * a02 + b1 * a12 + b2 * a22 + b3 * a32;
            out[3] = b0 * a03 + b1 * a13 + b2 * a23 + b3 * a33;

            b0 = b[4];
            b1 = b[5];
            b2 = b[6];
            b3 = b[7];
            out[4] = b0 * a00 + b1 * a10 + b2 * a20 + b3 * a30;
            out[5] = b0 * a01 + b1 * a11 + b2 * a21 + b3 * a31;
            out[6] = b0 * a02 + b1 * a12 + b2 * a22 + b3 * a32;
            out[7] = b0 * a03 + b1 * a13 + b2 * a23 + b3 * a33;

            b0 = b[8];
            b1 = b[9];
            b2 = b[10];
            b3 = b[11];
            out[8] = b0 * a00 + b1 * a10 + b2 * a20 + b3 * a30;
            out[9] = b0 * a01 + b1 * a11 + b2 * a21 + b3 * a31;
            out[10] = b0 * a02 + b1 * a12 + b2 * a22 + b3 * a32;
            out[11] = b0 * a03 + b1 * a13 + b2 * a23 + b3 * a33;

            b0 = b[12];
            b1 = b[13];
            b2 = b[14];
            b3 = b[15];
            out[12] = b0 * a00 + b1 * a10 + b2 * a20 + b3 * a30;
            out[13] = b0 * a01 + b1 * a11 + b2 * a21 + b3 * a31;
            out[14] = b0 * a02 + b1 * a12 + b2 * a22 + b3 * a32;
            out[15] = b0 * a03 + b1 * a13 + b2 * a23 + b3 * a33;
            return out;
        },
        identity: function (out) {
            out[0] = 1;
            out[1] = 0;
            out[2] = 0;
            out[3] = 0;
            out[4] = 0;
            out[5] = 1;
            out[6] = 0;
            out[7] = 0;
            out[8] = 0;
            out[9] = 0;
            out[10] = 1;
            out[11] = 0;
            out[12] = 0;
            out[13] = 0;
            out[14] = 0;
            out[15] = 1;
            return out;
        }
    };

    function initVertexBuffers(gl) {
        var latitudeBands = 50;
        var longitudeBands = 50;
        var radius = 2;

        var vertexPositionData = [];
        var normalData = [];
        var textureCoordData = [];
        var indexData = [];

        var latNumber, longNumber;

        // Calculate sphere vertex positions, normals, and texture coordinates.
        for (latNumber = 0; latNumber <= latitudeBands; ++latNumber) {
            var theta = latNumber * Math.PI / latitudeBands;
            var sinTheta = Math.sin(theta);
            var cosTheta = Math.cos(theta);

            for (longNumber = 0; longNumber <= longitudeBands; ++longNumber) {
                var phi = longNumber * 2 * Math.PI / longitudeBands;
                var sinPhi = Math.sin(phi);
                var cosPhi = Math.cos(phi);

                var x = cosPhi * sinTheta;
                var y = cosTheta;
                var z = sinPhi * sinTheta;

                var u = 1 - (longNumber / longitudeBands);
                var v = 1 - (latNumber / latitudeBands);

                vertexPositionData.push(radius * x);
                vertexPositionData.push(radius * y);
                vertexPositionData.push(radius * z);

                normalData.push(x);
                normalData.push(y);
                normalData.push(z);

                textureCoordData.push(u);
                textureCoordData.push(v);
            }
        }

        // Calculate sphere indices.
        for (latNumber = 0; latNumber < latitudeBands; ++latNumber) {
            for (longNumber = 0; longNumber < longitudeBands; ++longNumber) {
                var first = (latNumber * (longitudeBands + 1)) + longNumber;
                var second = first + longitudeBands + 1;

                indexData.push(first);
                indexData.push(second);
                indexData.push(first + 1);

                indexData.push(second);
                indexData.push(second + 1);
                indexData.push(first + 1);
            }
        }

        vertexPositionData = new Float32Array(vertexPositionData);
        normalData = new Float32Array(normalData);
        textureCoordData = new Float32Array(textureCoordData);
        indexData = new Uint16Array(indexData);

        // Create buffer objects.
        var vertexPositionBuffer = gl.createBuffer();
        var vertexNormalBuffer = gl.createBuffer();
        var indexBuffer = gl.createBuffer();

        // Write the vertex positions to their buffer object.
        gl.bindBuffer(gl.ARRAY_BUFFER, vertexPositionBuffer);
        gl.bufferData(gl.ARRAY_BUFFER, vertexPositionData, gl.STATIC_DRAW);

        // Assign position coords to attrib and enable it.
        var VertexPosition = gl.getAttribLocation(program, 'c');
        gl.vertexAttribPointer(VertexPosition, 3, gl.FLOAT, false, 0, 0);
        gl.enableVertexAttribArray(VertexPosition);

        // Write the normals to their buffer object.
        gl.bindBuffer(gl.ARRAY_BUFFER, vertexNormalBuffer);
        gl.bufferData(gl.ARRAY_BUFFER, normalData, gl.STATIC_DRAW);

        // Assign normal to attrib and enable it.
        var VertexNormal = gl.getAttribLocation(program, 'd');
        gl.vertexAttribPointer(VertexNormal, 3, gl.FLOAT, false, 0, 0);
        gl.enableVertexAttribArray(VertexNormal);

        // Pass index buffer data to element array buffer.
        gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer);
        gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, indexData, gl.STATIC_DRAW);

        return indexData.length;
    }

    function generate() {
        if (!(gl = getRenderingContext()))
            return;

        var vertexShader = gl.createShader(gl.VERTEX_SHADER);
        gl.shaderSource(vertexShader, VERTEX_SHADER);
        gl.compileShader(vertexShader);
        var fragmentShader = gl.createShader(gl.FRAGMENT_SHADER);
        gl.shaderSource(fragmentShader, FRAGMENT_SHADER);
        gl.compileShader(fragmentShader);
        program = gl.createProgram();
        gl.attachShader(program, vertexShader);
        gl.attachShader(program, fragmentShader);
        gl.linkProgram(program);
        gl.detachShader(program, vertexShader);
        gl.detachShader(program, fragmentShader);
        gl.deleteShader(vertexShader);
        gl.deleteShader(fragmentShader);
        gl.useProgram(program);

        // Init vertex buffers (position, color, and index data).
        var n = initVertexBuffers(gl);

        // Set up clear color and enable depth testing.
        gl.clearColor(0.0, 0.0, 0.0, 1.0);
        gl.enable(gl.DEPTH_TEST);

        // Create projection matrix.
        var projection = mat4.create();
        mat4.perspective(projection, Math.PI / 6, 1.0, 0.1, 100.0);

        // Create model-view matrix.
        var modelView = mat4.create();
        mat4.lookAt(modelView, [0.0, 0.0, 10.0], [0.0, 0.0, 0.0], [0.0, 1.0, 0.0]);

        // Multiply the projection matrix by the model-view matrix to create the mvpMatrix.
        var mvpMatrix = mat4.create();
        mat4.multiply(mvpMatrix, projection, modelView);

        // Pass the modelView matrix into the shader.
        var ModelViewMatrix = gl.getUniformLocation(program, 'h');
        gl.uniformMatrix4fv(ModelViewMatrix, false, modelView);

        // Pass the mvp matrix into the shader.
        var MVP = gl.getUniformLocation(program, 'i');
        gl.uniformMatrix4fv(MVP, false, mvpMatrix);

        // Pass the light position into the shader.
        var LightPosition = gl.getUniformLocation(program, 'e');
        gl.uniform4fv(LightPosition, [10.0, 10.0, 10.0, 1.0]);

        // Pass the material diffuse color into the shader.
        var Kd = gl.getUniformLocation(program, 'f');
        gl.uniform3fv(Kd, [0.9, 0.5, 0.3]);

        // Pass the light diffuse color into the shader.
        var Ld = gl.getUniformLocation(program, 'g');
        gl.uniform3fv(Ld, [1.0, 1.0, 1.0]);

        // Clear & draw.
        gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
        gl.drawElements(gl.TRIANGLES, n, gl.UNSIGNED_SHORT, 0);

        cleanup();
        return canvas.toDataURL();
    }

    function cleanup() {
        gl.useProgram(null);
        if (program)
            gl.deleteProgram(program);
    }

    function getRenderingContext() {
        canvas.width = 67;
        canvas.height = 67;
        var gl = canvas.getContext("webgl")
            || canvas.getContext("experimental-webgl");
        if (gl) {
            gl.viewport(0, 0, 67, 67);
            gl.clearColor(0.0, 0.0, 0.0, 1.0);
            gl.clear(gl.COLOR_BUFFER_BIT);
        }
        return gl;
    }

    function toHash(str) {
        var h = 0x811c9dc5;
        for (var i = 0; i < str.length; ++i) {
            h ^= str.charCodeAt(i);
            h += (h << 1) + (h << 4) + (h << 7) + (h << 8) + (h << 24);
        }
        return h >>> 0;
    }

    var imageHash = 0;

    canvas = document.getElementById("canvas1");
    // canvas = document.createElement("canvas");
    if (canvas != null) {
        // Get the image data as a string.
        var imageData = generate();
        if (imageData) {
            // Hash the image data.
            imageHash = toHash(imageData);
        }
    }
    return imageHash;
}

var iPhoneDevices = {
    "Apple A5 GPU": {
        960: "iPhone 4s"
    },

    "Apple A6 GPU": {
        1136: "iPhone 5"
    },

    "Apple A7 GPU": {
        1136: "iPhone 5s",
        2048: "iPad"
    },

    "Apple A8 GPU": {
        1136: "iPod touch (6th gen)",
        1334: "iPhone 6",
        2208: "iPhone 6 Plus",
        2048: "iPad Air2/Mini4"
    },

    "Apple A9 GPU": {
        1136: "iPhone SE",
        1334: "iPhone 6s",
        2208: "iPhone 6s Plus"
    },
    "Apple A9X GPU": {
        2048: "iPad Pro (1st gen 9.7-inch)",
        2732: "iPad Pro (1st gen 12.9-inch)"
    },

    "Apple A10 GPU": {
        1334: "iPhone 7",
        2208: "iPhone 7 Plus",
        2224: "iPad Pro (2th gen 10.5-inch)",
        2732: "iPad Pro (2th gen 12.9-inch)"
    },
    "Apple A10X GPU": {
        2224: "iPad Pro (2th gen 10.5-inch)",
        2732: "iPad Pro (2th gen 12.9-inch)"
    },

    "Apple A11 GPU": {
        1334: "iPhone 8",
        2208: "iPhone 8 Plus",
        2436: "iPhone X"
    },
    "Apple A12 GPU": {
        1792: "iPhone XR",
        2436: "iPhone XS",
        2688: "iPhone XS MAX",
        2388: "iPad Pro (3rd gen 11-inch)",
        2732: "iPad Pro (3rd gen 12.9-inch)"
    },
    "Apple A12X GPU": {
        2388: "iPad Pro (3rd gen 11-inch)",
        2732: "iPad Pro (3rd gen 12.9-inch)"
    },

    "Apple A13 GPU": {
        1792: "iPhone 11",
        2436: "iPhone 11Pro",
        2688: "iPhone 11 Pro Max",
    },

    "Apple A14 GPU": {
        2340: "iPhone 12 mini",
        2532: "iPhone 12/12Pro",
        2778: "iPhone 12 Pro Max",
    },

    "Apple GPU": {
        960: "iPhone 4/4s",
        1136: "iPhone 5/5s/SE",
        1334: "iPhone 6/6S/7/8",
        1792: "iPhone 11/XR",
        2208: "iPhone 6 Plus/6s Plus/7 Plus/8 Plus",
        2340: "iPhone 12 mini",
        2436: "iPhone X/XS/11Pro",
        2532: "iPhone 12/12Pro",
        2688: "iPhone XS MAX/11 Pro Max",
        2778: "iPhone 12 Pro Max",

        2048: "iPad",
        2224: "iPad Pro (2th gen 10.5-inch)",
        2732: "iPad Pro",
    },

    "unknow": "unknown"
};
var iHash = {
    '105985484': "Apple A14 GPU",
    '4193218782': "Apple A13 GPU",
    '352823931': "Apple A13 GPU",
    '4085158452': "Apple A12 GPU",
    '958581112': "Apple A12 GPU",
    '411650080': "Apple A11 GPU",
    '2114570256': "Apple A10 GPU",
    '583354101': "Apple A10X GPU",
    '3458129248': "Apple A10 GPU",
}
var winOS = {
    version: {
        'ME': ['4.90'],
        'NT 3.11': ['NT3.51'],
        'NT 4.0': ['NT4.0'],
        '2000': ['NT 5.0'],
        'XP': ['NT 5.1', 'NT 5.2'],
        'Vista': ['NT 6.0'],
        '7': ['NT 6.1'],
        '8': ['NT 6.2'],
        '8.1': ['NT 6.3'],
        '10': ['NT 6.4', 'NT 10.0'],
        'RT': ['ARM']
    }
}


export default {
    /**
     * 厂商
     * @returns 
     */
    manufacturer: function () {
        var rules = {
            "Apple": "\\biPhone\\b|\\biPod\\b|\\bMac\\b|iPad|iPad.*Mobile",
            "BlackBerry": "BlackBerry|\\bBB10\\b|rim[0-9]+|\\b(BBA100|BBB100|BBD100|BBE100|BBF100|STH100)\\b-[0-9]+",
            "Pixel": "; \\bPixel\\b",
            "HTC": "HTC|HTC.*(Sensation|Evo|Vision|Explorer|6800|8100|8900|A7272|S510e|C110e|Legend|Desire|T8282)|APX515CKT|Qtek9090|APA9292KT|HD_mini|Sensation.*Z710e|PG86100|Z715e|Desire.*(A8181|HD)|ADR6200|ADR6400L|ADR6425|001HT|Inspire 4G|Android.*\\bEVO\\b|T-Mobile G1|Z520m|Android [0-9.]+; Pixel",
            "Nexus": "Nexus One|Nexus S|Galaxy.*Nexus|Android.*Nexus.*Mobile|Nexus 4|Nexus 5|Nexus 5X|Nexus 6",
            "Dell": "Dell[;]? (Streak|Aero|Venue|Venue Pro|Flash|Smoke|Mini 3iX)|XCD28|XCD35|\\b001DL\\b|\\b101DL\\b|\\bGS01\\b",
            "Motorola": "Motorola|DROIDX|DROID BIONIC|\\bDroid\\b.*Build|Android.*Xoom|HRI39|MOT-|A1260|A1680|A555|A853|A855|A953|A955|A956|Motorola.*ELECTRIFY|Motorola.*i1|i867|i940|MB200|MB300|MB501|MB502|MB508|MB511|MB520|MB525|MB526|MB611|MB612|MB632|MB810|MB855|MB860|MB861|MB865|MB870|ME501|ME502|ME511|ME525|ME600|ME632|ME722|ME811|ME860|ME863|ME865|MT620|MT710|MT716|MT720|MT810|MT870|MT917|Motorola.*TITANIUM|WX435|WX445|XT300|XT301|XT311|XT316|XT317|XT319|XT320|XT390|XT502|XT530|XT531|XT532|XT535|XT603|XT610|XT611|XT615|XT681|XT701|XT702|XT711|XT720|XT800|XT806|XT860|XT862|XT875|XT882|XT883|XT894|XT901|XT907|XT909|XT910|XT912|XT928|XT926|XT915|XT919|XT925|XT1021|\\bMoto E\\b|XT1068|XT1092|XT1052",
            "Samsung": "\\bSamsung\\b|SM-G950F|SM-G955F|SM-G9250|GT-19300|SGH-I337|BGT-S5230|GT-B2100|GT-B2700|GT-B2710|GT-B3210|GT-B3310|GT-B3410|GT-B3730|GT-B3740|GT-B5510|GT-B5512|GT-B5722|GT-B6520|GT-B7300|GT-B7320|GT-B7330|GT-B7350|GT-B7510|GT-B7722|GT-B7800|GT-C3010|GT-C3011|GT-C3060|GT-C3200|GT-C3212|GT-C3212I|GT-C3262|GT-C3222|GT-C3300|GT-C3300K|GT-C3303|GT-C3303K|GT-C3310|GT-C3322|GT-C3330|GT-C3350|GT-C3500|GT-C3510|GT-C3530|GT-C3630|GT-C3780|GT-C5010|GT-C5212|GT-C6620|GT-C6625|GT-C6712|GT-E1050|GT-E1070|GT-E1075|GT-E1080|GT-E1081|GT-E1085|GT-E1087|GT-E1100|GT-E1107|GT-E1110|GT-E1120|GT-E1125|GT-E1130|GT-E1160|GT-E1170|GT-E1175|GT-E1180|GT-E1182|GT-E1200|GT-E1210|GT-E1225|GT-E1230|GT-E1390|GT-E2100|GT-E2120|GT-E2121|GT-E2152|GT-E2220|GT-E2222|GT-E2230|GT-E2232|GT-E2250|GT-E2370|GT-E2550|GT-E2652|GT-E3210|GT-E3213|GT-I5500|GT-I5503|GT-I5700|GT-I5800|GT-I5801|GT-I6410|GT-I6420|GT-I7110|GT-I7410|GT-I7500|GT-I8000|GT-I8150|GT-I8160|GT-I8190|GT-I8320|GT-I8330|GT-I8350|GT-I8530|GT-I8700|GT-I8703|GT-I8910|GT-I9000|GT-I9001|GT-I9003|GT-I9010|GT-I9020|GT-I9023|GT-I9070|GT-I9082|GT-I9100|GT-I9103|GT-I9220|GT-I9250|GT-I9300|GT-I9305|GT-I9500|GT-I9505|GT-M3510|GT-M5650|GT-M7500|GT-M7600|GT-M7603|GT-M8800|GT-M8910|GT-N7000|GT-S3110|GT-S3310|GT-S3350|GT-S3353|GT-S3370|GT-S3650|GT-S3653|GT-S3770|GT-S3850|GT-S5210|GT-S5220|GT-S5229|GT-S5230|GT-S5233|GT-S5250|GT-S5253|GT-S5260|GT-S5263|GT-S5270|GT-S5300|GT-S5330|GT-S5350|GT-S5360|GT-S5363|GT-S5369|GT-S5380|GT-S5380D|GT-S5560|GT-S5570|GT-S5600|GT-S5603|GT-S5610|GT-S5620|GT-S5660|GT-S5670|GT-S5690|GT-S5750|GT-S5780|GT-S5830|GT-S5839|GT-S6102|GT-S6500|GT-S7070|GT-S7200|GT-S7220|GT-S7230|GT-S7233|GT-S7250|GT-S7500|GT-S7530|GT-S7550|GT-S7562|GT-S7710|GT-S8000|GT-S8003|GT-S8500|GT-S8530|GT-S8600|SCH-A310|SCH-A530|SCH-A570|SCH-A610|SCH-A630|SCH-A650|SCH-A790|SCH-A795|SCH-A850|SCH-A870|SCH-A890|SCH-A930|SCH-A950|SCH-A970|SCH-A990|SCH-I100|SCH-I110|SCH-I400|SCH-I405|SCH-I500|SCH-I510|SCH-I515|SCH-I600|SCH-I730|SCH-I760|SCH-I770|SCH-I830|SCH-I910|SCH-I920|SCH-I959|SCH-LC11|SCH-N150|SCH-N300|SCH-R100|SCH-R300|SCH-R351|SCH-R400|SCH-R410|SCH-T300|SCH-U310|SCH-U320|SCH-U350|SCH-U360|SCH-U365|SCH-U370|SCH-U380|SCH-U410|SCH-U430|SCH-U450|SCH-U460|SCH-U470|SCH-U490|SCH-U540|SCH-U550|SCH-U620|SCH-U640|SCH-U650|SCH-U660|SCH-U700|SCH-U740|SCH-U750|SCH-U810|SCH-U820|SCH-U900|SCH-U940|SCH-U960|SCS-26UC|SGH-A107|SGH-A117|SGH-A127|SGH-A137|SGH-A157|SGH-A167|SGH-A177|SGH-A187|SGH-A197|SGH-A227|SGH-A237|SGH-A257|SGH-A437|SGH-A517|SGH-A597|SGH-A637|SGH-A657|SGH-A667|SGH-A687|SGH-A697|SGH-A707|SGH-A717|SGH-A727|SGH-A737|SGH-A747|SGH-A767|SGH-A777|SGH-A797|SGH-A817|SGH-A827|SGH-A837|SGH-A847|SGH-A867|SGH-A877|SGH-A887|SGH-A897|SGH-A927|SGH-B100|SGH-B130|SGH-B200|SGH-B220|SGH-C100|SGH-C110|SGH-C120|SGH-C130|SGH-C140|SGH-C160|SGH-C170|SGH-C180|SGH-C200|SGH-C207|SGH-C210|SGH-C225|SGH-C230|SGH-C417|SGH-C450|SGH-D307|SGH-D347|SGH-D357|SGH-D407|SGH-D415|SGH-D780|SGH-D807|SGH-D980|SGH-E105|SGH-E200|SGH-E315|SGH-E316|SGH-E317|SGH-E335|SGH-E590|SGH-E635|SGH-E715|SGH-E890|SGH-F300|SGH-F480|SGH-I200|SGH-I300|SGH-I320|SGH-I550|SGH-I577|SGH-I600|SGH-I607|SGH-I617|SGH-I627|SGH-I637|SGH-I677|SGH-I700|SGH-I717|SGH-I727|SGH-i747M|SGH-I777|SGH-I780|SGH-I827|SGH-I847|SGH-I857|SGH-I896|SGH-I897|SGH-I900|SGH-I907|SGH-I917|SGH-I927|SGH-I937|SGH-I997|SGH-J150|SGH-J200|SGH-L170|SGH-L700|SGH-M110|SGH-M150|SGH-M200|SGH-N105|SGH-N500|SGH-N600|SGH-N620|SGH-N625|SGH-N700|SGH-N710|SGH-P107|SGH-P207|SGH-P300|SGH-P310|SGH-P520|SGH-P735|SGH-P777|SGH-Q105|SGH-R210|SGH-R220|SGH-R225|SGH-S105|SGH-S307|SGH-T109|SGH-T119|SGH-T139|SGH-T209|SGH-T219|SGH-T229|SGH-T239|SGH-T249|SGH-T259|SGH-T309|SGH-T319|SGH-T329|SGH-T339|SGH-T349|SGH-T359|SGH-T369|SGH-T379|SGH-T409|SGH-T429|SGH-T439|SGH-T459|SGH-T469|SGH-T479|SGH-T499|SGH-T509|SGH-T519|SGH-T539|SGH-T559|SGH-T589|SGH-T609|SGH-T619|SGH-T629|SGH-T639|SGH-T659|SGH-T669|SGH-T679|SGH-T709|SGH-T719|SGH-T729|SGH-T739|SGH-T746|SGH-T749|SGH-T759|SGH-T769|SGH-T809|SGH-T819|SGH-T839|SGH-T919|SGH-T929|SGH-T939|SGH-T959|SGH-T989|SGH-U100|SGH-U200|SGH-U800|SGH-V205|SGH-V206|SGH-X100|SGH-X105|SGH-X120|SGH-X140|SGH-X426|SGH-X427|SGH-X475|SGH-X495|SGH-X497|SGH-X507|SGH-X600|SGH-X610|SGH-X620|SGH-X630|SGH-X700|SGH-X820|SGH-X890|SGH-Z130|SGH-Z150|SGH-Z170|SGH-ZX10|SGH-ZX20|SHW-M110|SPH-A120|SPH-A400|SPH-A420|SPH-A460|SPH-A500|SPH-A560|SPH-A600|SPH-A620|SPH-A660|SPH-A700|SPH-A740|SPH-A760|SPH-A790|SPH-A800|SPH-A820|SPH-A840|SPH-A880|SPH-A900|SPH-A940|SPH-A960|SPH-D600|SPH-D700|SPH-D710|SPH-D720|SPH-I300|SPH-I325|SPH-I330|SPH-I350|SPH-I500|SPH-I600|SPH-I700|SPH-L700|SPH-M100|SPH-M220|SPH-M240|SPH-M300|SPH-M305|SPH-M320|SPH-M330|SPH-M350|SPH-M360|SPH-M370|SPH-M380|SPH-M510|SPH-M540|SPH-M550|SPH-M560|SPH-M570|SPH-M580|SPH-M610|SPH-M620|SPH-M630|SPH-M800|SPH-M810|SPH-M850|SPH-M900|SPH-M910|SPH-M920|SPH-M930|SPH-N100|SPH-N200|SPH-N240|SPH-N300|SPH-N400|SPH-Z400|SWC-E100|SCH-i909|GT-N7100|GT-N7105|SCH-I535|SM-N900A|SGH-I317|SGH-T999L|GT-S5360B|GT-I8262|GT-S6802|GT-S6312|GT-S6310|GT-S5312|GT-S5310|GT-I9105|GT-I8510|GT-S6790N|SM-G7105|SM-N9005|GT-S5301|GT-I9295|GT-I9195|SM-C101|GT-S7392|GT-S7560|GT-B7610|GT-I5510|GT-S7582|GT-S7530E|GT-I8750|SM-G9006V|SM-G9008V|SM-G9009D|SM-G900A|SM-G900D|SM-G900F|SM-G900H|SM-G900I|SM-G900J|SM-G900K|SM-G900L|SM-G900M|SM-G900P|SM-G900R4|SM-G900S|SM-G900T|SM-G900V|SM-G900W8|SHV-E160K|SCH-P709|SCH-P729|SM-T2558|GT-I9205|SM-G9350|SM-J120F|SM-G920F|SM-G920V|SM-G930F|SM-N910C|SM-A310F|GT-I9190|SM-J500FN|SM-G903F|SM-J330F|SM-G610F|SM-G981B|SM-G892A|SM-A530F",
            "LG": "\\bLG\\b;|LG[- ]?(C800|C900|E400|E610|E900|E-900|F160|F180K|F180L|F180S|730|855|L160|LS740|LS840|LS970|LU6200|MS690|MS695|MS770|MS840|MS870|MS910|P500|P700|P705|VM696|AS680|AS695|AX840|C729|E970|GS505|272|C395|E739BK|E960|L55C|L75C|LS696|LS860|P769BK|P350|P500|P509|P870|UN272|US730|VS840|VS950|LN272|LN510|LS670|LS855|LW690|MN270|MN510|P509|P769|P930|UN200|UN270|UN510|UN610|US670|US740|US760|UX265|UX840|VN271|VN530|VS660|VS700|VS740|VS750|VS910|VS920|VS930|VX9200|VX11000|AX840A|LW770|P506|P925|P999|E612|D955|D802|MS323|M257)|LM-G710",
            "Sony": "SonyST|SonyLT|SonyEricsson|SonyEricssonLT15iv|LT18i|E10i|LT28h|LT26w|SonyEricssonMT27i|C5303|C6902|C6903|C6906|C6943|D2533|SOV34|601SO|F8332",
            "Asus": "Asus.*Galaxy|PadFone.*Mobile",
            "Xiaomi": "^(?!.*\\bx11\\b).*xiaomi.*$|POCOPHONE F1|MI 8|Redmi Note 9S|Redmi Note 5A Prime|N2G47H|M2001J2G|M2001J2I|M1805E10A|M2004J11G|M1902F1G|M2002J9G|M2004J19G|M2003J6A1G",
            "NokiaLumia": "Lumia [0-9]{3,4}",
            "Micromax": "Micromax.*\\b(A210|A92|A88|A72|A111|A110Q|A115|A116|A110|A90S|A26|A51|A35|A54|A25|A27|A89|A68|A65|A57|A90)\\b",
            "Palm": "PalmSource|Palm",
            "Vertu": "Vertu|Vertu.*Ltd|Vertu.*Ascent|Vertu.*Ayxta|Vertu.*Constellation(F|Quest)?|Vertu.*Monika|Vertu.*Signature",
            "Pantech": "PANTECH|IM-A850S|IM-A840S|IM-A830L|IM-A830K|IM-A830S|IM-A820L|IM-A810K|IM-A810S|IM-A800S|IM-T100K|IM-A725L|IM-A780L|IM-A775C|IM-A770K|IM-A760S|IM-A750K|IM-A740S|IM-A730S|IM-A720L|IM-A710K|IM-A690L|IM-A690S|IM-A650S|IM-A630K|IM-A600S|VEGA PTL21|PT003|P8010|ADR910L|P6030|P6020|P9070|P4100|P9060|P5000|CDM8992|TXT8045|ADR8995|IS11PT|P2030|P6010|P8000|PT002|IS06|CDM8999|P9050|PT001|TXT8040|P2020|P9020|P2000|P7040|P7000|C790",
            "Fly": "IQ230|IQ444|IQ450|IQ440|IQ442|IQ441|IQ245|IQ256|IQ236|IQ255|IQ235|IQ245|IQ275|IQ240|IQ285|IQ280|IQ270|IQ260|IQ250",
            "Wiko": "KITE 4G|HIGHWAY|GETAWAY|STAIRWAY|DARKSIDE|DARKFULL|DARKNIGHT|DARKMOON|SLIDE|WAX 4G|RAINBOW|BLOOM|SUNSET|GOA(?!nna)|LENNY|BARRY|IGGY|OZZY|CINK FIVE|CINK PEAX|CINK PEAX 2|CINK SLIM|CINK SLIM 2|CINK +|CINK KING|CINK PEAX|CINK SLIM|SUBLIM",
            "iMobile": "i-mobile (IQ|i-STYLE|idea|ZAA|Hitz)",
            "SimValley": "\\b(SP-80|XT-930|SX-340|XT-930|SX-310|SP-360|SP60|SPT-800|SP-120|SPT-800|SP-140|SPX-5|SPX-8|SP-100|SPX-8|SPX-12)\\b",
            "Wolfgang": "AT-B24D|AT-AS50HD|AT-AS40W|AT-AS55HD|AT-AS45q2|AT-B26D|AT-AS50Q",
            "Alcatel": "Alcatel",
            "Nintendo": "Nintendo (3DS|Switch)",
            "Amoi": "Amoi",
            "INQ": "INQ",
            "OnePlus": "ONEPLUS",
            "GenericPhone": "Tapatalk|PDA;|SAGEM|\\bmmp\\b|pocket|\\bpsp\\b|symbian|Smartphone|smartfon|treo|up.browser|up.link|vodafone|\\bwap\\b|nokia|Series40|Series60|S60|SonyEricsson|N900|MAUI.*WAP.*Browser"
        }
        var userAgent = window.navigator.userAgent;
        for (var key in rules) {
            var regExp = new RegExp(rules[key], 'i');
            if (regExp.test(userAgent)) {
                return key;
            }
        }
        return 'unknown';
    },
    /**
     * 设备型号
     * @returns 设备型号
     */
    modelCode: function () {
        var deviceType = "unknown";
        var userAgent = window.navigator.userAgent;
        var gpu = baseInfo.getWebglRenderer() || "unknown";
        var agents = userAgent.split(/[(,)]/);
        var info = agents[1] || "unknown";
        if (userAgent.indexOf("iPad") > -1) {
            deviceType = "iPad" + hash3d();
        } else if (userAgent.indexOf("iPod") > -1) {
            deviceType = "iPod" + hash3d();
        } else if (userAgent.indexOf("iPhone") > -1) {
            deviceType = "iPhone";
            if (gpu.indexOf('Apple GPU') > -1) {
                gpu = iHash[hash3d()] || 'Apple GPU'
            }
            var device = iPhoneDevices[gpu];
            if (device && device !== "unknown") {
                deviceType = (device[Math.max(screen.width, screen.height) * window.devicePixelRatio] || "iPhone-") + '-' + hash3d();
            }

        } else if (info.indexOf("Android") > -1) {
            deviceType = "Android Phone";
            var androidInfo = info.split(";");
            var hasInfo = 0;
            androidInfo.forEach(function (s) {
                var idx = s.indexOf("Build/");
                if (idx > -1) {
                    deviceType = s.substring(0, idx).trim();
                    hasInfo = 1
                }
            });
            if (hasInfo === 0 && androidInfo.length >= 3) {
                deviceType = androidInfo[2].trim();
            }
        } else if (info.indexOf("Windows Phone") > -1) {
            deviceType = "Windows Phone";
        } else if (info.indexOf("Windows NT") > -1) {
            deviceType = "Windows";
            var idre = info.match(/NT ([\d\.]{3,4})/g)
            if (idre.length > 0) {
                var winOSKey = Object.keys(winOS.version)
                for (var i = 0; i < winOSKey.length; i++) {
                    if (winOS.version[winOSKey[i]].indexOf(idre[0]) > -1) {
                        deviceType = "Windows " + i
                        break
                    }
                }
            }
        } else if (info.indexOf('Mac OS X') > -1) {
            deviceType = "Mac-" + hash3d();
        } else {
            deviceType = "other";
        }
        return deviceType;
    },
    /**
     * 判断是否为虚拟机
     * @returns 
     */
    getVMinfo: function () {
        function gcd(a, b) {
            if (a < 0.00000001) {
                return b;
            }
            if (a < b) {
                return gcd(b - Math.floor(b / a) * a, a);
            } else if (a == b) {
                return a;
            } else {
                return gcd(b, a);
            }
        }
        try {
            var x_init = performance.now() / 1000;

            var g = performance.now() / 1000 - x_init;
            for (var i = 0; i < 10; i++) {
                g = gcd(g, performance.now() / 1000 - x_init);
            }
            return Math.round(1 / g)
        } catch (e) {
            console.error('no support performance');
            return false
        }

    },
    /**
     * 系统
     * @returns 
     */
    systemName: function () {
        var rules = {
            "Android": "Android",
            "iOS": "iPhone|\\biPhone|\\biPhone.*Mobile|\\biPod|\\biPad|AppleCoreMedia",
            "BlackBerry": "blackberry|\\bBB10\\b|rim tablet os",
            "Palm": "PalmOS|avantgo|blazer|elaine|hiptop|palm|plucker|xiino",
            "Symbian": "Symbian|SymbOS|Series60|Series40|SYB-[0-9]+|\\bS60\\b",
            "WindowsMobile": "Windows CE.*(PPC|Smartphone|Mobile|[0-9]{3}x[0-9]{3})|Windows Mobile|Windows Phone [0-9.]+|WCE;",
            "WindowsPhone": "Windows Phone 10.0|Windows Phone 8.1|Windows Phone 8.0|Windows Phone OS|XBLWP7|ZuneWP7|Windows NT 6.[23]; ARM;",
            "Sailfish": "Sailfish",
            "MeeGo": "MeeGo",
            "Maemo": "Maemo",
            "Java": "J2ME\/|\\bMIDP\\b|\\bCLDC\\b",
            "webOS": "webOS|hpwOS",
            "bada": "\\bBada\\b",
            "BREW": "BREW",
            "Windows": "Windows",
            "Mac": "Mac OS",
            "Linux": "Linux",
        };
        var userAgent = window.navigator.userAgent;
        for (var key in rules) {
            var regExp = new RegExp(rules[key], 'i');
            if (regExp.test(userAgent)) {
                return key;
            }
        }
        return 'unknown';
    },
    /**
     * 系统版本
     * @returns 
     */
    systemVersion: function () {
        var props = {
            "Windows": ["Windows NT [VER]"],
            "Mac": ["Mac OS X [VER]"],
            "Linux": ["Linux  [VER]"],
            "Android": ["Android [VER]"],
            "iOS": [
                " \\bi?OS\\b [VER][ ;]{1}",
                "Version\/[VER]"
            ],
            "BlackBerry": [
                "BlackBerry[\\w]+\/[VER]",
                "BlackBerry.*Version\/[VER]",
                "Version\/[VER]"
            ],
            "Palm": ["PalmOS|avantgo|blazer|elaine|hiptop|palm|plucker|xiino [VER]"],
            "Symbian": [
                "SymbianOS\/[VER]",
                "Symbian\/[VER]"
            ],
            "WindowsMobile": ["Windows CE\/[VER]"],
            "WindowsPhone": [
                "Windows Phone OS [VER]",
                "Windows Phone [VER]"
            ],
            "Sailfish": ["Sailfish [VER]"],
            "MeeGo": ["MeeGo [VER]"],
            "Maemo": ["Maemo [VER]"],
            "Java": ["Java\/[VER]"],
            "webOS": [
                "webOS\/[VER]",
                "hpwOS\/[VER];"
            ],
            "bada": ["Bada [VER]"],
            "BREW": ["BREW [VER]"]
        };
        var propertyName = this.systemName();
        var patternsArray = props[propertyName];
        for (var patterns of patternsArray) {
            var verPos = patterns.indexOf('[VER]');
            if (verPos >= 0) {
                patterns = patterns.substring(0, verPos) + '([\\w._\\+]+)' + patterns.substring(verPos + 5);
            }
            var regExp = new RegExp(patterns, 'i');
            var match = regExp.exec(window.navigator.userAgent);
            if (match !== null) {
                return match[1];
            }
        }
        return null;
    },
    /**
     * 浏览器语言
     * @returns 
     */
    languages: function () {
        var acceptedLanguages = [];
        if (navigator.languages) {
            for (var i = 0; i < navigator.languages.length; i++) {
                acceptedLanguages.push(navigator.languages[i].toString());
            }
        } else {
            acceptedLanguages.push(navigator.language || navigator.userLanguage || navigator.browserLanguage || navigator.systemLanguage || '');
        }
        return acceptedLanguages
    },
    timeZone: function () {
        var _a;
        var DateTimeFormat = (_a = window.Intl) === null || _a === void 0 ? void 0 : _a.DateTimeFormat;
        if (DateTimeFormat) {
            var timezone = new DateTimeFormat().resolvedOptions().timeZone;
            if (timezone) {
                return timezone + '(' + (new Date().getTimezoneOffset()) + ')';
            }
        }
        // For browsers that don't support timezone names
        // The minus is intentional because the JS offset is opposite to the real offset
        var offset = -this.getTimezoneOffset();
        return "UTC" + (offset >= 0 ? '+' : '') + Math.abs(offset);
    },
    getTimezoneOffset: function () {
        var currentYear = new Date().getFullYear();
        // The timezone offset may change over time due to daylight saving time (DST) shifts.
        // The non-DST timezone offset is used as the result timezone offset.
        // Since the DST season differs in the northern and the southern hemispheres,
        // both January and July timezones offsets are considered.
        return Math.max(
            // `getTimezoneOffset` returns a number as a string in some unidentified cases
            parseFloat(new Date(currentYear, 0, 1).getTimezoneOffset()), parseFloat(new Date(currentYear, 6, 1).getTimezoneOffset()));
    },
}