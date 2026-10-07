const express = require("express");
const cors = require("cors");
const dotenv = require("dotenv");
const path = require("path");

dotenv.config();

const app = express();

const PORT = 3000;


// =========================
// MIDDLEWARE
// =========================

app.use(cors());

app.use(express.json());

app.use(express.urlencoded({
    extended: true
}));


// =========================
// SERVE FRONTEND FILES
// =========================

app.use(express.static(__dirname));


// =========================
// HOME PAGE
// =========================

app.get("/", (req, res) => {

    res.sendFile(
        path.join(__dirname, "login.html")
    );

});


// =========================
// TEST BACKEND
// =========================

app.get("/api/test", (req, res) => {

    res.json({

        success: true,

        message:
            "CodeCoach AI backend is working!"

    });

});


// =========================
// LOGIN API
// =========================

app.post("/api/users/login", (req, res) => {

    const {
        email,
        password
    } = req.body;


    console.log(
        "Login request received:",
        email
    );


    // Check fields

    if (!email || !password) {

        return res.status(400).json({

            success: false,

            message:
                "Email and password are required."

        });

    }


    // Temporary authentication

    if (password.length < 6) {

        return res.status(401).json({

            success: false,

            message:
                "Invalid email or password."

        });

    }


    // Login successful

    return res.status(200).json({

        success: true,

        message:
            "Login successful",

        user: {

            email: email

        }

    });

});


// =========================
// START SERVER
// =========================

app.listen(PORT, () => {

    console.log(
        `CodeCoach AI backend running at http://localhost:${PORT}`
    );

});