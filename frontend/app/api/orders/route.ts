import { NextRequest, NextResponse } from "next/server";

const API_GATEWAY_URL = process.env.API_GATEWAY_URL ?? "http://localhost:8082";

async function toClientResponse(response: Response): Promise<NextResponse> {
  const body = await response.text();
  const contentType = response.headers.get("content-type") ?? "application/json";

  return new NextResponse(body, {
    status: response.status,
    headers: { "content-type": contentType },
  });
}

export async function GET(request: NextRequest) {
  try {
    const status = request.nextUrl.searchParams.get("status");
    const url = new URL("/api/orders", API_GATEWAY_URL);
    if (status) {
      url.searchParams.set("status", status);
    }

    const authHeader = request.headers.get("authorization");
    const response = await fetch(url.toString(), {
      cache: "no-store",
      headers: authHeader ? { Authorization: authHeader } : undefined,
    });
    return await toClientResponse(response);
  } catch {
    return NextResponse.json(
      { error: "Backend service unavailable." },
      { status: 502 },
    );
  }
}

export async function POST(request: NextRequest) {
  try {
    const payload = await request.text();
    const authHeader = request.headers.get("authorization");
    const response = await fetch(`${API_GATEWAY_URL}/api/orders`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(authHeader ? { Authorization: authHeader } : {}),
      },
      body: payload,
    });

    return await toClientResponse(response);
  } catch {
    return NextResponse.json(
      { error: "Backend service unavailable." },
      { status: 502 },
    );
  }
}
